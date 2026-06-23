package com.fallguys.inventoryservice.messaging.consumer;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fallguys.inventoryservice.stock.domain.command.InboundCommand;
import com.fallguys.inventoryservice.stock.domain.command.InboundLine;

/**
 * 입고 요청 이벤트의 업무 payload(PO 입고·SO 도착 공통 — sales/procurement 동일 형태). executor는 메시지에 실려 온다(JWT 없음).
 * 계약 위반은 {@link #validate()}에서 MalformedEventException으로 던져 DLQ로 보낸다.
 *
 * <p>형태가 OutboundCommandPayload와 동일하지만, toCommand()가 InboundCommand를 반환한다(추후 공통 payload로 통합 가능).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InboundCommandPayload(
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
    public InboundCommand toCommand() {
        List<InboundLine> commandLines = lines.stream()
                .map(line -> new InboundLine(line.sku(), line.quantity(), line.sourceLineNo()))
                .toList();
        return new InboundCommand(sourceRef, warehouseCode, commandLines, executor.empNo(), executor.name());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
