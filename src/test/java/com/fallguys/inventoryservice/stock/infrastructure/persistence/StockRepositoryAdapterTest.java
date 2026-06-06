package com.fallguys.inventoryservice.stock.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.fallguys.inventoryservice.config.JpaAuditingConfig;
import com.fallguys.inventoryservice.stock.domain.Stock;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;

import jakarta.persistence.EntityManager;

@DataJpaTest
@Import({StockRepositoryAdapter.class, JpaAuditingConfig.class})
class StockRepositoryAdapterTest {

    @Autowired
    private StockRepositoryAdapter adapter;

    @Autowired
    private TestEntityManager testEntityManager;

    @BeforeEach
    void seed() {
        insertWarehouse(2L, "WH-SE-001", "서울 1창고");
    }

    @Test
    void save하면_id를_발급하고_findResultById가_창고코드를_조인하며_createdAt이_채워진다() {
        Long id = adapter.save(Stock.create("HMC-EN-00214", "엔진오일 필터", 2L, 100, 50));

        assertThat(id).isNotNull();

        StockCreateResult result = adapter.findResultById(id).orElseThrow();
        assertThat(result.stockId()).isEqualTo(id);
        assertThat(result.sku()).isEqualTo("HMC-EN-00214");
        assertThat(result.warehouseCode()).isEqualTo("WH-SE-001");
        assertThat(result.quantity()).isEqualTo(100);
        assertThat(result.safetyStock()).isEqualTo(50);
        assertThat(result.createdAt()).isNotNull();
    }

    @Test
    void existsBySkuAndWarehouseId는_저장된_조합에만_true다() {
        adapter.save(Stock.create("HMC-EN-00214", "엔진오일 필터", 2L, 100, 50));

        assertThat(adapter.existsBySkuAndWarehouseId("HMC-EN-00214", 2L)).isTrue();
        assertThat(adapter.existsBySkuAndWarehouseId("HMC-EN-00214", 999L)).isFalse();
        assertThat(adapter.existsBySkuAndWarehouseId("OTHER-SKU", 2L)).isFalse();
    }

    private void insertWarehouse(long id, String code, String name) {
        entityManager().createNativeQuery("""
                        INSERT INTO warehouse
                            (id, code, name, type, branch_id, address, active,
                             created_by, updated_by, created_at, updated_at, version)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .setParameter(1, id)
                .setParameter(2, code)
                .setParameter(3, name)
                .setParameter(4, "HQ")
                .setParameter(5, null)
                .setParameter(6, "주소")
                .setParameter(7, true)
                .setParameter(8, "EMP-001")
                .setParameter(9, "EMP-001")
                .setParameter(10, Instant.parse("2024-01-01T00:00:00Z"))
                .setParameter(11, Instant.parse("2024-01-01T00:00:00Z"))
                .setParameter(12, 0L)
                .executeUpdate();
    }

    private EntityManager entityManager() {
        return testEntityManager.getEntityManager();
    }
}
