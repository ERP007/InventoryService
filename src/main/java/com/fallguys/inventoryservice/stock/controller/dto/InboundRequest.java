package com.fallguys.inventoryservice.stock.controller.dto;

import java.util.List;

import com.fallguys.inventoryservice.stock.domain.command.InboundCommand;
import com.fallguys.inventoryservice.stock.domain.command.InboundLine;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * 재고 입고 요청(내부). 한 창고의 여러 라인을 한 번에 입고한다. 수행자(사번·이름)는 바디가 아니라 전파된 JWT에서 채운다.
 */
public record InboundRequest(
        @NotBlank String sourceRef,
        @NotBlank String warehouseCode,
        @NotEmpty @Valid List<InboundLineRequest> lines
) {

    public InboundRequest {
        sourceRef = sourceRef == null ? null : sourceRef.trim();
        warehouseCode = warehouseCode == null ? null : warehouseCode.trim();
    }

    public InboundCommand toCommand(String executorEmpNo, String executorName) {
        List<InboundLine> commandLines = lines == null ? List.of()
                : lines.stream().map(InboundLineRequest::toLine).toList();
        return new InboundCommand(sourceRef, warehouseCode, commandLines, executorEmpNo, executorName);
    }
}
