package com.fallguys.inventoryservice.stock.domain;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fallguys.inventoryservice.stock.domain.command.CreateStockCommand;
import com.fallguys.inventoryservice.stock.domain.exception.StockAlreadyExistsException;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;
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
     * (sku × warehouse) 재고 행을 신규 생성한다. 입출고 흐름 밖의 초기 데이터 적재·개발 검증용(ADMIN 전용).
     *
     * 흐름:
     * 1) warehouseCode로 실제 창고 id를 해석한다(없으면 404 — 존재 은닉).
     * 2) (sku × warehouseId) 중복 여부를 확인한다(이미 있으면 409, 조정을 사용해야 함).
     * 3) 도메인 모델로 재고를 생성한다(수량·안전재고 ≥ 0 불변식은 도메인이 검증).
     * 4) 저장 후 창고 코드를 조인한 생성 결과를 재조회해 반환한다.
     * (TODO) StockMovement(reason=INIT/ADJUST) 1건 기록 — 재고 이동 이력 도메인 도입 시 추가.
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
                command.sku(), command.itemName(), warehouseId, command.quantity(), command.safetyStock());
        Long id = stockRepository.save(stock);

        return stockRepository.findResultById(id)
                .orElseThrow(() -> new IllegalStateException("저장된 재고를 조회하지 못했습니다: " + id));
    }
}
