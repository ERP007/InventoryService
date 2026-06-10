package com.fallguys.inventoryservice.stock.infrastructure.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fallguys.inventoryservice.stock.domain.MovementType;
import com.fallguys.inventoryservice.stock.domain.query.InboundMovement;
import com.fallguys.inventoryservice.stock.domain.query.MovementHistory;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummary;

public interface StockMovementJpaDao extends JpaRepository<StockMovementEntity, Long> {

    /**
     * 조회 조건으로 이동 이력을 검색해 읽기 모델(MovementSummary)로 투영한다.
     *
     * 조인: WarehouseEntity(창고 코드·이름)만 INNER로 매칭한다. 부품명·단위는 이동 이력 자체 스냅샷(m.itemName·m.itemUnit)을 쓴다.
     * 필터(각 조건은 파라미터로 on/off):
     *   - keyword: 부품명/SKU 부분 일치(대소문자 무시, 호출부가 소문자 LIKE 패턴을 만들어 전달).
     *   - warehouseCodes: hasWarehouseFilter=true일 때만 w.code IN 으로 제한.
     *   - type: null이 아니면 이동 유형 일치.
     *   - 기간: performedAt ∈ [fromInstant, toExclusive)(끝일 포함을 위해 상한은 종료일+1일 00:00).
     * 정렬·페이지: Pageable로 위임(정렬식은 어댑터가 화이트리스트로 구성, id tie-breaker 포함).
     */
    @Query(value = """
            SELECT new com.fallguys.inventoryservice.stock.domain.query.MovementSummary(
                m.id, m.performedAt, m.sku, m.itemName, m.itemUnit, w.code, w.name,
                m.delta, m.type, m.reason, m.sourceRef, m.executorEmpNo)
            FROM StockMovementEntity m
            JOIN WarehouseEntity w ON w.id = m.warehouseId
            WHERE (:keyword IS NULL OR LOWER(m.itemName) LIKE :keyword OR LOWER(m.sku) LIKE :keyword)
              AND (:hasWarehouseFilter = FALSE OR w.code IN :warehouseCodes)
              AND (:type IS NULL OR m.type = :type)
              AND m.performedAt >= :fromInstant
              AND m.performedAt < :toExclusive
            """,
            countQuery = """
            SELECT COUNT(m)
            FROM StockMovementEntity m
            JOIN WarehouseEntity w ON w.id = m.warehouseId
            WHERE (:keyword IS NULL OR LOWER(m.itemName) LIKE :keyword OR LOWER(m.sku) LIKE :keyword)
              AND (:hasWarehouseFilter = FALSE OR w.code IN :warehouseCodes)
              AND (:type IS NULL OR m.type = :type)
              AND m.performedAt >= :fromInstant
              AND m.performedAt < :toExclusive
            """)
    Page<MovementSummary> search(
            @Param("keyword") String keyword,
            @Param("hasWarehouseFilter") boolean hasWarehouseFilter,
            @Param("warehouseCodes") List<String> warehouseCodes,
            @Param("type") MovementType type,
            @Param("fromInstant") Instant fromInstant,
            @Param("toExclusive") Instant toExclusive,
            Pageable pageable);

    /**
     * sku의 최근 이동 이력을 시각 내림차순으로 조회한다(상세 패널, 건수 제한은 Pageable로 위임).
     * 조인: WarehouseEntity는 warehouseCodes 필터(테넌시)용이다. 동일 시각은 id 내림차순으로 최신을 우선한다.
     */
    @Query("""
            SELECT new com.fallguys.inventoryservice.stock.domain.query.MovementHistory(
                m.type, m.delta, m.executorEmpNo, m.executorName, m.performedAt)
            FROM StockMovementEntity m
            JOIN WarehouseEntity w ON w.id = m.warehouseId
            WHERE m.sku = :sku
              AND (:hasWarehouseFilter = FALSE OR w.code IN :warehouseCodes)
            ORDER BY m.performedAt DESC, m.id DESC
            """)
    List<MovementHistory> findRecentBySku(
            @Param("sku") String sku,
            @Param("hasWarehouseFilter") boolean hasWarehouseFilter,
            @Param("warehouseCodes") List<String> warehouseCodes,
            Pageable pageable);

    /**
     * since 이후 이동 건수를 센다(KPI 최근 7일). 조인: WarehouseEntity는 warehouseCodes 필터(테넌시)용이다.
     */
    @Query("""
            SELECT COUNT(m)
            FROM StockMovementEntity m
            JOIN WarehouseEntity w ON w.id = m.warehouseId
            WHERE (:hasWarehouseFilter = FALSE OR w.code IN :warehouseCodes)
              AND m.performedAt >= :since
            """)
    long countRecent(
            @Param("hasWarehouseFilter") boolean hasWarehouseFilter,
            @Param("warehouseCodes") List<String> warehouseCodes,
            @Param("since") Instant since);

    /**
     * (sourceRef × 창고)의 INBOUND 이동 이력을 결과 투영(InboundMovement)으로 조회한다(입고 멱등 replay).
     * 조인: WarehouseEntity를 (m.warehouseId = w.id)로 조인해 창고 코드로 한정한다. id 오름차순(적재 순서)으로 반환한다.
     */
    @Query("""
            SELECT new com.fallguys.inventoryservice.stock.domain.query.InboundMovement(
                m.id, m.sku, m.delta, m.stockAfter)
            FROM StockMovementEntity m
            JOIN WarehouseEntity w ON w.id = m.warehouseId
            WHERE m.sourceRef = :sourceRef AND w.code = :warehouseCode
              AND m.type = com.fallguys.inventoryservice.stock.domain.MovementType.INBOUND
            ORDER BY m.id
            """)
    List<InboundMovement> findInboundBySourceRefAndWarehouseCode(
            @Param("sourceRef") String sourceRef, @Param("warehouseCode") String warehouseCode);
}
