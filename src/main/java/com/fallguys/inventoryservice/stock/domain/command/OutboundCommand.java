package com.fallguys.inventoryservice.stock.domain.command;

import java.util.List;

/**
 * 재고 출고 유스케이스 입력(SO 출고). 한 창고의 여러 라인을 한 트랜잭션으로 차감한다.
 * executorEmpNo·executorName은 컨트롤러가 전파된 JWT(employee_no·name)에서 채운다(이동 이력의 수행자 스냅샷).
 */
public record OutboundCommand(
        String sourceRef,
        String warehouseCode,
        List<OutboundLine> lines,
        String executorEmpNo,
        String executorName
) {
}
