package com.fallguys.inventoryservice.stock.controller.dto;

import java.util.List;

import com.fallguys.inventoryservice.stock.domain.command.OutboundCommand;
import com.fallguys.inventoryservice.stock.domain.command.OutboundLine;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * 재고 출고 요청(내부). 한 창고의 여러 라인을 한 번에 출고한다. 수행자(사번·이름)는 바디가 아니라 전파된 JWT에서 채운다.
 */
public record OutboundRequest(
        @NotBlank String sourceRef,
        @NotBlank String warehouseCode,
        @NotEmpty @Valid List<OutboundLineRequest> lines
) {

    public OutboundRequest {
        sourceRef = sourceRef == null ? null : sourceRef.trim();
        warehouseCode = warehouseCode == null ? null : warehouseCode.trim();
    }

    public OutboundCommand toCommand(String executorEmpNo, String executorName) {
        List<OutboundLine> commandLines = lines == null ? List.of()
                : lines.stream().map(OutboundLineRequest::toLine).toList();
        return new OutboundCommand(sourceRef, warehouseCode, commandLines, executorEmpNo, executorName);
    }
}
