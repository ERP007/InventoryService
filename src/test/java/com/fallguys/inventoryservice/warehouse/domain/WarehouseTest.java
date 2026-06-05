package com.fallguys.inventoryservice.warehouse.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.warehouse.domain.command.CreateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseBranchRuleException;
import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;

class WarehouseTest {

    @Test
    void DEALER는_branchId와_함께_생성되며_active는_true이고_id는_null이다() {
        Warehouse warehouse = Warehouse.create(
                new CreateWarehouseCommand("WH-SE-002", "서울 2창고", WarehouseType.DEALER, 3L, "서울 강남구"));

        assertThat(warehouse.getId()).isNull();
        assertThat(warehouse.getType()).isEqualTo(WarehouseType.DEALER);
        assertThat(warehouse.getBranchId()).isEqualTo(3L);
        assertThat(warehouse.isActive()).isTrue();
    }

    @Test
    void HQ는_branchId없이_생성된다() {
        Warehouse warehouse = Warehouse.create(
                new CreateWarehouseCommand("HQ-001", "본사 중앙창고", WarehouseType.HQ, null, null));

        assertThat(warehouse.getBranchId()).isNull();
        assertThat(warehouse.isActive()).isTrue();
    }

    @Test
    void DEALER인데_branchId가_없으면_WarehouseBranchRuleException() {
        assertThatThrownBy(() -> Warehouse.create(
                new CreateWarehouseCommand("WH-SE-002", "서울 2창고", WarehouseType.DEALER, null, null)))
                .isInstanceOf(WarehouseBranchRuleException.class);
    }

    @Test
    void HQ인데_branchId가_있으면_WarehouseBranchRuleException() {
        assertThatThrownBy(() -> Warehouse.create(
                new CreateWarehouseCommand("HQ-001", "본사 중앙창고", WarehouseType.HQ, 3L, null)))
                .isInstanceOf(WarehouseBranchRuleException.class);
    }
}
