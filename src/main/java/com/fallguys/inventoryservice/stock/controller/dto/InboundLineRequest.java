package com.fallguys.inventoryservice.stock.controller.dto;

import com.fallguys.inventoryservice.stock.domain.command.InboundLine;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 입고 요청의 한 라인. quantity는 1 이상, sourceLineNo는 원천 문서 라인 식별자(임의 정수 허용).
 */
public record InboundLineRequest(
        @NotBlank String sku,
        @NotNull @Positive Integer quantity,
        @NotNull Integer sourceLineNo
) {

    public InboundLineRequest {
        sku = sku == null ? null : sku.trim();
    }

    public InboundLine toLine() {
        return new InboundLine(sku, quantity, sourceLineNo);
    }
}
