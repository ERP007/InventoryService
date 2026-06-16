package com.fallguys.inventoryservice.stock.domain;

import java.util.List;
import java.util.Optional;

import com.fallguys.inventoryservice.stock.domain.command.UpdateSafetyStockCommand;
import com.fallguys.inventoryservice.stock.domain.query.ItemStockRow;
import com.fallguys.inventoryservice.stock.domain.query.SafetyStockEdit;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;
import com.fallguys.inventoryservice.stock.domain.query.StockDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockQuantity;
import com.fallguys.inventoryservice.stock.domain.query.StockSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuRow;
import com.fallguys.inventoryservice.stock.domain.query.StockStatusCount;
import com.fallguys.inventoryservice.stock.domain.query.StockSummaryPage;

/**
 * [DIP] 재고 영속성 추상화. 도메인이 정의하고 infrastructure가 구현한다.
 */
public interface StockRepository {

    /** 조회 조건(검색·창고필터·상태·정렬·페이지)에 맞는 재고 목록 페이지를 반환한다. 매칭이 없으면 빈 페이지. */
    StockSummaryPage search(StockSearchQuery query);

    /** (창고 코드 × sku) 단건 재고를 조회한다. 재고 행이 없으면 empty(빈 stock fallback은 서비스가 결정). */
    Optional<StockDetail> findDetailByWarehouseCodeAndSku(String warehouseCode, String sku);

    /** (창고 코드 × SKU 집합)의 현재고·안전재고를 일괄 조회한다(내부 호출). 재고 행이 없는 SKU는 결과에서 생략된다. */
    List<StockQuantity> findQuantitiesByWarehouseCodeAndSkus(String warehouseCode, List<String> skus);

    /** (sku × warehouse) 조합의 재고 존재 여부. 신규 생성 전 중복 검사에 사용한다. */
    boolean existsBySkuAndWarehouseId(String sku, Long warehouseId);

    /** 신규 재고를 저장하고 발급된 식별자를 반환한다. */
    Long save(Stock stock);

    /** 저장 직후 식별자로 생성 결과(창고 코드 조인 포함)를 조회한다. 없으면 empty. */
    Optional<StockCreateResult> findResultById(Long id);

    /** sku의 창고별 재고 행을 조회한다(상세 패널). warehouseCodes가 비어있으면 전체 창고, 있으면 해당 창고로 한정. */
    List<StockSkuRow> findSkuWarehouseStocks(String sku, List<String> warehouseCodes);

    /**
     * sku의 창고별 현재고를 최근 수정(updated_at) 내림차순으로 최대 limit건 조회한다(부품 마스터 화면 창고별 현재고 패널).
     * warehouseCodes가 비어있으면 전사, 있으면 해당 창고로 한정한다. 비활성 창고는 제외하고, 비활성 부품(SKU)은 포함한다(단순 조회).
     * 기본값 빈 리스트는 영속 구현체(StockRepositoryAdapter)가 반드시 override한다 — 이 조회와 무관한 테스트 stub의 보일러플레이트를 줄이기 위한 기본값일 뿐이다.
     */
    default List<ItemStockRow> findRecentItemStocks(String sku, List<String> warehouseCodes, int limit) {
        return List.of();
    }

    /** 범위 내 (sku × warehouse) 포지션의 총/부족/무재고 수를 센다(KPI). warehouseCodes가 비어있으면 전사. */
    StockStatusCount countByStatus(List<String> warehouseCodes);

    /** 조정 대상 재고를 (sku × warehouseCode)로 조회한다(수정용 — 같은 트랜잭션에서 save 시 @Version 적용). 없으면 empty. */
    Optional<Stock> findBySkuAndWarehouseCode(String sku, String warehouseCode);

    /**
     * 출고 대상 재고를 (sku × warehouseId)로 비관락(PESSIMISTIC_WRITE)으로 조회한다(출고용 — 동시 차감을 직렬화해 음수 재고를 막는다).
     * 잠금은 트랜잭션 커밋/롤백까지 유지되며, 잠금 대기 초과는 PessimisticLockingFailureException(LOCK_TIMEOUT, 409)으로 매핑된다. 없으면 empty.
     */
    Optional<Stock> findBySkuAndWarehouseIdForUpdate(String sku, Long warehouseId);

    /** 안전재고 조정 프리필용: (창고 코드 × sku)의 현재 안전재고·현재고·부품정보·version을 조회한다. 행이 없으면 empty. */
    Optional<SafetyStockEdit> findSafetyStockEdit(String warehouseCode, String sku);

    /**
     * (sku × warehouseCode) 행의 안전재고를 절대값으로 교체하고 갱신 결과(version 포함)를 반환한다.
     * 클라이언트 version이 현재와 다르면 OptimisticLockConflictException(409), 행이 없으면 StockNotFoundException(404).
     */
    SafetyStockEdit updateSafetyStock(UpdateSafetyStockCommand command);
}
