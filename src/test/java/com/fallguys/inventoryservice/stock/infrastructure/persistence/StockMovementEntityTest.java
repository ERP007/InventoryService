package com.fallguys.inventoryservice.stock.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.stock.domain.MovementReason;
import com.fallguys.inventoryservice.stock.domain.MovementType;
import com.fallguys.inventoryservice.stock.domain.StockMovement;

class StockMovementEntityTest {

    @Test
    void 신규_도메인을_엔티티로_변환하면_식별자와_발생시각은_비어있다() {
        StockMovement m = StockMovement.createAdjustment(
                "HMC-EN-00214", 2L, -3, MovementType.DECREASE, MovementReason.DAMAGE, 48, "파손", "HMC0001");

        StockMovementEntity e = StockMovementEntity.from(m);

        assertThat(e.getId()).isNull();
        assertThat(e.getPerformedAt()).isNull();
        assertThat(e.getSourceRef()).isNull();
        assertThat(e.getSourceLineNo()).isNull();
        assertThat(e.getSku()).isEqualTo("HMC-EN-00214");
        assertThat(e.getWarehouseId()).isEqualTo(2L);
        assertThat(e.getDelta()).isEqualTo(-3);
        assertThat(e.getType()).isEqualTo(MovementType.DECREASE);
        assertThat(e.getReason()).isEqualTo(MovementReason.DAMAGE);
        assertThat(e.getStockAfter()).isEqualTo(48);
        assertThat(e.getMemo()).isEqualTo("파손");
        assertThat(e.getExecutorEmpNo()).isEqualTo("HMC0001");
    }

    @Test
    void 엔티티를_도메인으로_되돌리면_필드가_보존된다() {
        StockMovement origin = StockMovement.createAdjustment(
                "HMC-EN-00214", 2L, 5, MovementType.INCREASE, MovementReason.FOUND, 56, "재고 발견", "HMC0001");

        StockMovement restored = StockMovementEntity.from(origin).toDomain();

        assertThat(restored.getSku()).isEqualTo(origin.getSku());
        assertThat(restored.getWarehouseId()).isEqualTo(origin.getWarehouseId());
        assertThat(restored.getDelta()).isEqualTo(origin.getDelta());
        assertThat(restored.getType()).isEqualTo(origin.getType());
        assertThat(restored.getReason()).isEqualTo(origin.getReason());
        assertThat(restored.getStockAfter()).isEqualTo(origin.getStockAfter());
        assertThat(restored.getMemo()).isEqualTo(origin.getMemo());
        assertThat(restored.getExecutorEmpNo()).isEqualTo(origin.getExecutorEmpNo());
    }
}
