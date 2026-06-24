package com.fallguys.inventoryservice.stock.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import com.fallguys.inventoryservice.stock.domain.ItemUnit;
import com.fallguys.inventoryservice.stock.domain.query.ItemStockRow;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;
import com.fallguys.inventoryservice.stock.domain.query.StockDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockQuantity;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuRow;
import com.fallguys.inventoryservice.stock.domain.query.StockStatusCount;
import com.fallguys.inventoryservice.stock.domain.query.StockSummary;

public interface StockJpaDao extends JpaRepository<StockEntity, Long> {

    /**
     * 조회 조건으로 재고를 검색해 읽기 모델(StockSummary)로 투영한다.
     *
     * 조인: WarehouseEntity를 (s.warehouseId = w.id)로 조인해 창고 코드·이름을 가져온다.
     * 필터(각 조건은 파라미터로 on/off):
     *   - keyword: 부품명/SKU 부분 일치(대소문자 무시, 호출부가 소문자 LIKE 패턴을 만들어 전달).
     *   - warehouseCodes: hasWarehouseFilter=true일 때만 w.code IN 으로 제한.
     *   - status: 'NORMAL'/'LOW' 문자열을 현재고·안전재고 비교로 번역(파생 상태를 SQL로 표현). LOW는 안전재고 미만(재고 0 포함).
     * 정렬·페이지: Pageable로 위임(정렬식은 어댑터가 화이트리스트로 구성, id tie-breaker 포함).
     */
    @Query(value = """
            SELECT new com.fallguys.inventoryservice.stock.domain.query.StockSummary(
                s.id, s.sku, s.itemName, s.itemUnit, s.warehouseId, w.code, w.name, s.currentStock, s.safetyStock, s.updatedAt, w.active, s.itemActive)
            FROM StockEntity s
            JOIN WarehouseEntity w ON w.id = s.warehouseId
            WHERE (:keyword IS NULL OR LOWER(s.itemName) LIKE :keyword OR LOWER(s.sku) LIKE :keyword)
              AND (:hasWarehouseFilter = FALSE OR w.code IN :warehouseCodes)
              AND (:status IS NULL
                   OR (:status = 'LOW' AND s.currentStock < s.safetyStock)
                   OR (:status = 'NORMAL' AND s.currentStock >= s.safetyStock))
              AND (:includeInactive = TRUE OR (w.active = TRUE AND s.itemActive = TRUE))
            """,
            countQuery = """
            SELECT COUNT(s)
            FROM StockEntity s
            JOIN WarehouseEntity w ON w.id = s.warehouseId
            WHERE (:keyword IS NULL OR LOWER(s.itemName) LIKE :keyword OR LOWER(s.sku) LIKE :keyword)
              AND (:hasWarehouseFilter = FALSE OR w.code IN :warehouseCodes)
              AND (:status IS NULL
                   OR (:status = 'LOW' AND s.currentStock < s.safetyStock)
                   OR (:status = 'NORMAL' AND s.currentStock >= s.safetyStock))
              AND (:includeInactive = TRUE OR (w.active = TRUE AND s.itemActive = TRUE))
            """)
    Page<StockSummary> search(
            @Param("keyword") String keyword,
            @Param("hasWarehouseFilter") boolean hasWarehouseFilter,
            @Param("warehouseCodes") List<String> warehouseCodes,
            @Param("status") String status,
            @Param("includeInactive") boolean includeInactive,
            Pageable pageable);

    /** (sku × warehouse) 재고 존재 여부(등록 전 중복 검사). */
    boolean existsBySkuAndWarehouseId(String sku, Long warehouseId);

    /**
     * 저장 직후 식별자로 생성 결과를 조회한다.
     *
     * 조인: WarehouseEntity를 (s.warehouseId = w.id)로 조인해 창고 코드를 가져온다(매핑 연관 없이 ON 조인).
     * createdAt은 Auditing(@CreatedDate)이 채운 값을 그대로 반환한다.
     */
    @Query("""
            SELECT new com.fallguys.inventoryservice.stock.domain.query.StockCreateResult(
                s.id, s.sku, w.code, s.currentStock, s.safetyStock, s.createdAt)
            FROM StockEntity s
            JOIN WarehouseEntity w ON w.id = s.warehouseId
            WHERE s.id = :id
            """)
    Optional<StockCreateResult> findResultById(@Param("id") Long id);

    /**
     * (창고 코드 × sku) 단건 재고를 조회한다(SO 발주 라인 표시용).
     * 조인: WarehouseEntity를 (s.warehouseId = w.id)로 조인해 창고 코드로 식별한다.
     * 재고 행이 없으면 결과가 비어 빈 stock(0/0) fallback은 서비스가 결정한다.
     */
    @Query("""
            SELECT new com.fallguys.inventoryservice.stock.domain.query.StockDetail(
                w.code, s.sku, s.currentStock, s.safetyStock)
            FROM StockEntity s
            JOIN WarehouseEntity w ON w.id = s.warehouseId
            WHERE w.code = :warehouseCode AND s.sku = :sku
            """)
    Optional<StockDetail> findDetailByWarehouseCodeAndSku(
            @Param("warehouseCode") String warehouseCode, @Param("sku") String sku);

    /**
     * (창고 코드 × SKU 집합)의 현재고·안전재고를 일괄 투영한다(내부 일괄 조회).
     * 조인: WarehouseEntity를 (s.warehouseId = w.id)로 조인해 창고 코드로 한정한다.
     * 재고 행이 없는 SKU는 결과에 포함되지 않는다(호출 측이 0으로 간주).
     */
    @Query("""
            SELECT new com.fallguys.inventoryservice.stock.domain.query.StockQuantity(
                s.sku, s.currentStock, s.safetyStock)
            FROM StockEntity s
            JOIN WarehouseEntity w ON w.id = s.warehouseId
            WHERE w.code = :warehouseCode AND s.sku IN :skus
            """)
    List<StockQuantity> findQuantitiesByWarehouseCodeAndSkus(
            @Param("warehouseCode") String warehouseCode, @Param("skus") List<String> skus);

    /**
     * sku의 창고별 재고 행을 부품명·창고(code·name) 조인으로 조회한다(상세 패널).
     * 조인: WarehouseEntity를 (s.warehouseId = w.id)로 조인. warehouseCodes 필터는 hasWarehouseFilter로 on/off한다.
     * 비활성 창고 행은 제외한다(상세 패널은 활성 창고만 노출). itemActive는 비활성 부품 차단 판정에 쓴다.
     */
    @Query("""
            SELECT new com.fallguys.inventoryservice.stock.domain.query.StockSkuRow(
                s.itemName, s.itemUnit, s.warehouseId, w.code, w.name, s.currentStock, s.safetyStock, s.itemActive)
            FROM StockEntity s
            JOIN WarehouseEntity w ON w.id = s.warehouseId
            WHERE s.sku = :sku
              AND w.active = true
              AND (:hasWarehouseFilter = FALSE OR w.code IN :warehouseCodes)
            ORDER BY w.code
            """)
    List<StockSkuRow> findSkuWarehouseStocks(
            @Param("sku") String sku,
            @Param("hasWarehouseFilter") boolean hasWarehouseFilter,
            @Param("warehouseCodes") List<String> warehouseCodes);

    /**
     * sku의 창고별 현재고를 최근 수정 순으로 투영한다(부품 마스터 화면 창고별 현재고 패널, 건수 제한은 Pageable로 위임).
     * 조인: WarehouseEntity를 (s.warehouseId = w.id)로 조인해 창고 코드·이름을 가져온다. warehouseCodes 필터는 hasWarehouseFilter로 on/off한다.
     * 비활성 창고 행은 제외한다(active = true). 비활성 부품(SKU)은 거르지 않는다(단순 조회라 노출). 동일 시각은 id 내림차순으로 최신을 우선한다.
     */
    @Query("""
            SELECT new com.fallguys.inventoryservice.stock.domain.query.ItemStockRow(
                w.code, w.name, s.currentStock, s.safetyStock)
            FROM StockEntity s
            JOIN WarehouseEntity w ON w.id = s.warehouseId
            WHERE s.sku = :sku
              AND w.active = true
              AND (:hasWarehouseFilter = FALSE OR w.code IN :warehouseCodes)
            ORDER BY s.updatedAt DESC, s.id DESC
            """)
    List<ItemStockRow> findRecentItemStocks(
            @Param("sku") String sku,
            @Param("hasWarehouseFilter") boolean hasWarehouseFilter,
            @Param("warehouseCodes") List<String> warehouseCodes,
            Pageable pageable);

    /** sku를 가진 stock 행들의 창고 코드를 code 오름차순으로 조회한다(아이템 동기화 응답용). 창고 활성 여부와 무관하게 전 행을 본다. */
    @Query("""
            SELECT w.code
            FROM StockEntity s
            JOIN WarehouseEntity w ON w.id = s.warehouseId
            WHERE s.sku = :sku
            ORDER BY w.code ASC
            """)
    List<String> findWarehouseCodesBySku(@Param("sku") String sku);

    /**
     * sku를 가진 모든 stock 행의 item_name을 일괄 교체한다(Item 마스터 이름 동기화). 변경된 행 수를 반환한다.
     * 비정규화 미러 갱신이라 @Version·감사 컬럼은 건드리지 않는다(수량 변화가 아님). 창고 활성 여부와 무관하게 전 행을 갱신한다.
     */
    @Modifying
    @Query("UPDATE StockEntity s SET s.itemName = :itemName WHERE s.sku = :sku")
    int updateItemNameBySku(@Param("sku") String sku, @Param("itemName") String itemName);

    /**
     * sku를 가진 모든 stock 행의 item_unit을 일괄 교체한다(Item 마스터 단위 동기화). 변경된 행 수를 반환한다.
     * 비정규화 미러 갱신이라 @Version·감사 컬럼은 건드리지 않는다. 창고 활성 여부와 무관하게 전 행을 갱신한다.
     */
    @Modifying
    @Query("UPDATE StockEntity s SET s.itemUnit = :itemUnit WHERE s.sku = :sku")
    int updateItemUnitBySku(@Param("sku") String sku, @Param("itemUnit") ItemUnit itemUnit);

    /**
     * sku를 가진 모든 stock 행의 item_active를 일괄 교체한다(Item 마스터 활성 동기화). 변경된 행 수를 반환한다.
     * 비정규화 미러 갱신이라 @Version·감사 컬럼은 건드리지 않는다. 창고 활성 여부와 무관하게 전 행을 갱신한다.
     */
    @Modifying
    @Query("UPDATE StockEntity s SET s.itemActive = :active WHERE s.sku = :sku")
    int updateItemActiveBySku(@Param("sku") String sku, @Param("active") boolean active);

    /**
     * 범위 내 포지션의 총·부족 수를 한 번에 센다(KPI). 상태는 저장 컬럼이 아니라 현재고·안전재고로 파생한다.
     * COUNT(CASE …)로 부족(현재고 &lt; 안전재고, 재고 0 포함)을 세어 결과가 없어도 null 없이 0을 반환한다(정상 = 총 - 부족).
     * 비활성 창고·비활성 아이템(SKU)의 재고는 집계에서 제외한다(KPI는 운영 중 창고·부품만 반영).
     */
    @Query("""
            SELECT new com.fallguys.inventoryservice.stock.domain.query.StockStatusCount(
                COUNT(s),
                COUNT(CASE WHEN s.currentStock < s.safetyStock THEN 1 END))
            FROM StockEntity s
            JOIN WarehouseEntity w ON w.id = s.warehouseId
            WHERE w.active = true
              AND s.itemActive = true
              AND (:hasWarehouseFilter = FALSE OR w.code IN :warehouseCodes)
            """)
    StockStatusCount countByStatus(
            @Param("hasWarehouseFilter") boolean hasWarehouseFilter,
            @Param("warehouseCodes") List<String> warehouseCodes);

    /**
     * 조정 대상 재고 엔티티를 (sku × 창고코드)로 조회한다(수정용).
     * 엔티티가 영속 컨텍스트에 관리되어, 이어지는 update·flush에서 @Version 낙관락이 적용된다.
     */
    @Query("""
            SELECT s
            FROM StockEntity s
            JOIN WarehouseEntity w ON w.id = s.warehouseId
            WHERE s.sku = :sku AND w.code = :warehouseCode
            """)
    Optional<StockEntity> findBySkuAndWarehouseCode(
            @Param("sku") String sku, @Param("warehouseCode") String warehouseCode);

    /**
     * 출고 대상 재고 엔티티를 (sku × warehouseId)로 비관락(SELECT … FOR UPDATE)으로 조회한다.
     * 같은 (sku × warehouse) 행에 대한 동시 출고를 직렬화해 가용재고 검증과 차감 사이의 경합(음수 재고)을 막는다.
     * stock 단일 테이블만 잠그도록 warehouse 조인 없이 warehouse_id로 직접 매칭한다(호출부가 코드→id를 미리 해석).
     * jakarta.persistence.lock.timeout(ms) 힌트로 잠금 대기 상한을 둔다(초과 시 LockTimeoutException → 409 LOCK_TIMEOUT).
     * (PostgreSQL은 timeout=0이면 FOR UPDATE NOWAIT, 양수 대기는 세션 lock_timeout에 의존한다.)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
            SELECT s
            FROM StockEntity s
            WHERE s.sku = :sku AND s.warehouseId = :warehouseId
            """)
    Optional<StockEntity> findBySkuAndWarehouseIdForUpdate(
            @Param("sku") String sku, @Param("warehouseId") Long warehouseId);
}
