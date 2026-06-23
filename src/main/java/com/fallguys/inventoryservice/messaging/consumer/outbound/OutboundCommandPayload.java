package com.fallguys.inventoryservice.messaging.consumer.outbound;

import com.fallguys.inventoryservice.messaging.consumer.MalformedEventException;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fallguys.inventoryservice.stock.domain.command.OutboundCommand;
import com.fallguys.inventoryservice.stock.domain.command.OutboundLine;

/**
 * 출고 요청 이벤트의 업무 payload. executor는 동기 경로의 JWT 대신 메시지에 실려 온다(stock_movement NOT NULL 컬럼).
 * 수량·라인번호는 누락 감지를 위해 박스 타입으로 받고, {@link #validate()}에서 계약을 검증한다(위반 시 MalformedEventException → DLQ).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OutboundCommandPayload(
        String sourceRef,
        String warehouseCode,
        Executor executor,
        List<Line> lines
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Executor(String empNo, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(String sku, Integer quantity, Integer sourceLineNo) {
    }

    /** 계약 검증. 위반은 재시도 불가한 형식 오류이므로 MalformedEventException으로 던져 DLQ로 보낸다. */
    public void validate() {
        if (isBlank(sourceRef) || isBlank(warehouseCode)) {
            throw new MalformedEventException("sourceRef·warehouseCode는 필수입니다.");
        }
        if (executor == null || isBlank(executor.empNo()) || isBlank(executor.name())) {
            throw new MalformedEventException("executor(empNo·name)는 필수입니다.");
        }
        if (lines == null || lines.isEmpty()) {
            throw new MalformedEventException("lines는 한 건 이상이어야 합니다.");
        }
        for (Line line : lines) {
            if (isBlank(line.sku()) || line.quantity() == null || line.quantity() <= 0 || line.sourceLineNo() == null) {
                throw new MalformedEventException("라인 형식 오류(sku·quantity>0·sourceLineNo 필수): " + line);
            }
        }
    }

    /** 검증을 통과한 payload를 도메인 커맨드로 변환한다. */
    public OutboundCommand toCommand() {
        List<OutboundLine> commandLines = lines.stream()
                .map(line -> new OutboundLine(line.sku(), line.quantity(), line.sourceLineNo()))
                .toList();
        return new OutboundCommand(sourceRef, warehouseCode, commandLines, executor.empNo(), executor.name());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
