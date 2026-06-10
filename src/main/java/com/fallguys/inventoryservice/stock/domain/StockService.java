package com.fallguys.inventoryservice.stock.domain;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import com.fallguys.inventoryservice.shared.model.TenancyType;
import com.fallguys.inventoryservice.stock.domain.command.CreateStockCommand;
import com.fallguys.inventoryservice.stock.domain.exception.StockAlreadyExistsException;
import com.fallguys.inventoryservice.stock.domain.exception.StockNotFoundException;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;
import com.fallguys.inventoryservice.stock.domain.query.StockDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockQuantity;
import com.fallguys.inventoryservice.stock.domain.query.StockSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.StockSummaryPage;
import com.fallguys.inventoryservice.stock.domain.query.WarehouseStockQuery;
import com.fallguys.inventoryservice.warehouse.domain.WarehouseRepository;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseNotFoundException;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    // 같은 inventory 서비스 내 warehouse 애그리거트 참조(코드→id 해석, 존재 검증)
    private final WarehouseRepository warehouseRepository;

    /**
     * 재고 목록을 조회한다. Tenancy에 따라 조회 범위가 다르다.
     *
     * 흐름:
     * 1) BRANCH 사용자는 자기 창고(tenancy_code)로만 한정한다 — 요청의 창고 필터를 자기 창고로 강제 교체.
     * 2) ADMIN·HQ는 전사 범위로 요청 조건(창고 다중 필터 포함)을 그대로 사용한다.
     * 3) 검증된 조건으로 영속성에서 페이지 조회한다(status는 quantity·safetyStock 비교로 번역).
     *
     * 트랜잭션: 읽기 전용. 외부 호출 없음. 매칭 0건이면 빈 페이지.
     */
    @Transactional(readOnly = true)
    public StockSummaryPage search(StockSearchQuery query, TenancyType tenancyType, String tenancyCode) {
        StockSearchQuery effective = tenancyType == TenancyType.BRANCH
                ? query.withWarehouseCodes(List.of(tenancyCode))
                : query;
        return stockRepository.search(effective);
    }

    /**
     * (창고 × SKU 집합)의 현재고·안전재고를 일괄 조회한다. 서비스 간 내부 호출 전용이라 Tenancy 범위 강제는 없다.
     *
     * 흐름:
     * 1) 대상 창고가 존재하는지 확인한다 — 없으면 404로 막는다(잘못된 창고 코드를 빈 결과로 숨기지 않는다).
     * 2) (창고 × 요청 SKU들)의 재고 수량을 조회한다. 재고 행이 없는 (sku×창고)는 결과에서 생략된다(호출 측이 0으로 간주).
     *
     * 트랜잭션: 읽기 전용. 외부 호출 없음.
     *
     * 예외:
     * - 창고 없음: WarehouseNotFoundException (404)
     */
    @Transactional(readOnly = true)
    public List<StockQuantity> getStockQuantities(WarehouseStockQuery query) {
        if (!warehouseRepository.existsByCode(query.warehouseCode())) {
            throw new WarehouseNotFoundException(query.warehouseCode());
        }
        return stockRepository.findQuantitiesByWarehouseCodeAndSkus(query.warehouseCode(), query.skus());
    }

    /**
     * SO 발주 라인 추가 시 (창고 × 부품)의 현재고·안전재고를 조회한다. BRANCH 사용자 전용(인가는 컨트롤러).
     *
     * 흐름:
     * 1) 호출자의 담당 창고(tenancy_code)와 요청 warehouseCode를 동등 비교한다(외부 호출 없음).
     *    다르면 타 창고 존재를 은닉하기 위해 404(StockNotFoundException).
     * 2) (warehouseCode × sku) 재고 행을 조회한다. 있으면 그 값을, 없으면 quantity=0·safetyStock=0으로 응답한다(빈 stock).
     *
     * 트랜잭션: 읽기 전용. 외부 호출 없음.
     *
     * 예외:
     * - 담당 창고 불일치(존재 은닉): StockNotFoundException (404)
     *
     * TODO(Item 연동): sku가 Item 마스터에 없으면 404(STOCK_NOT_FOUND),
     *  빈 stock의 safetyStock은 Item 마스터 기본값으로 fallback. 현재는 검증 생략 + 0으로 응답.
     */
    @Transactional(readOnly = true)
    public StockDetail getDetail(String warehouseCode, String sku, String tenancyCode) {
        if (!Objects.equals(tenancyCode, warehouseCode)) {
            throw new StockNotFoundException(warehouseCode, sku);
        }
        return stockRepository.findDetailByWarehouseCodeAndSku(warehouseCode, sku)
                .orElseGet(() -> new StockDetail(warehouseCode, sku, 0, 0));
    }

    /**
     * (sku × warehouse) 재고 행을 신규 생성한다. 입출고 흐름 밖의 초기 데이터 적재·개발 검증용(ADMIN 전용).
     *
     * 흐름:
     * 1) warehouseCode로 실제 창고 id를 해석한다(없으면 404 — 존재 은닉).
     * 2) (sku × warehouseId) 중복 여부를 확인한다(이미 있으면 409, 조정을 사용해야 함).
     * 3) 도메인 모델로 재고를 생성한다(수량·안전재고 ≥ 0 불변식은 도메인이 검증).
     * 4) 저장 후 창고 코드를 조인한 생성 결과를 재조회해 반환한다.
     * 초기 적재는 이동 이력(StockMovement)을 남기지 않는다 — 입출고 흐름 밖이며, 누가/언제는 stock 감사 컬럼(created_by/created_at)으로 충분하다.
     *
     * 트랜잭션: 쓰기. 창고 미존재·중복은 저장 이전에 중단되어 아무것도 저장되지 않는다.
     * (지금은 sku의 Item 마스터 존재 검증을 하지 않는다 — 사용자가 모든 필드를 직접 입력하는 단순 적재.)
     *
     * 예외:
     * - 창고 없음: WarehouseNotFoundException (404)
     * - 재고 중복: StockAlreadyExistsException (409)
     */
    @Transactional
    public StockCreateResult create(CreateStockCommand command) {
        Long warehouseId = warehouseRepository.findForEditByCode(command.warehouseCode())
                .map(WarehouseSummaryForEdit::id)
                .orElseThrow(() -> new WarehouseNotFoundException(command.warehouseCode()));

        if (stockRepository.existsBySkuAndWarehouseId(command.sku(), warehouseId)) {
            throw new StockAlreadyExistsException(command.sku(), command.warehouseCode());
        }

        Stock stock = Stock.create(
                command.sku(), command.itemName(), command.itemUnit(), warehouseId,
                command.quantity(), command.safetyStock());
        Long id = stockRepository.save(stock);

        return stockRepository.findResultById(id)
                .orElseThrow(() -> new IllegalStateException("저장된 재고를 조회하지 못했습니다: " + id));
    }
}
