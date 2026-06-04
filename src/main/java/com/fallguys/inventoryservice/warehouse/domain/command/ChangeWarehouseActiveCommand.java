package com.fallguys.inventoryservice.warehouse.domain.command;

/**
 * 창고 활성 상태 전환 유스케이스 입력. 상태 변경이므로 Command record로 받는다.
 *
 * @param active  전환할 활성 상태
 * @param version 낙관적 락 버전(실제 전환 시 검증)
 */
public record ChangeWarehouseActiveCommand(boolean active, Long version) {
}
