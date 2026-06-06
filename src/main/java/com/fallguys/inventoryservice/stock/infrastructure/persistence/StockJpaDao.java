package com.fallguys.inventoryservice.stock.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;

public interface StockJpaDao extends JpaRepository<StockEntity, Long> {

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
}
