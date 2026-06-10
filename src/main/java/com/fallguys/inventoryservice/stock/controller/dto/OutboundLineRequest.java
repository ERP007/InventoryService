package com.fallguys.inventoryservice.stock.controller.dto;

import com.fallguys.inventoryservice.stock.domain.command.OutboundLine;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 출고 요청의 한 라인. quantity는 1 이상(출고 수량), sourceLineNo는 원천 문서(SO) 라인 식별자(임의 정수 허용).
 */
public record OutboundLineRequest(
        @NotBlank String sku,
        @NotNull @Positive Integer quantity,
        @NotNull Integer sourceLineNo
) {

    public OutboundLineRequest {
        sku = sku == null ? null : sku.trim();
    }

    public OutboundLine toLine() {
        return new OutboundLine(sku, quantity, sourceLineNo);
    }
}
