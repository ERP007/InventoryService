package com.fallguys.inventoryservice.stock.controller.dto;

import com.fallguys.inventoryservice.stock.domain.ItemUnit;

import jakarta.validation.constraints.NotNull;

/**
 * 아이템 단위 동기화 요청(internal). Item 마스터에서 변경된 단위를 받는다.
 * itemUnit은 ItemUnit enum(EA/BOX/SET/L)이며, 누락 시 @NotNull로 400, 허용 밖 값은 역직렬화 단계에서 400으로 처리된다.
 */
public record ItemUnitSyncRequest(
        @NotNull(message = "itemUnit은 필수입니다.")
        ItemUnit itemUnit
) {
}
