package com.fallguys.inventoryservice.stock.controller.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 아이템 이름 동기화 요청(internal). Item 마스터에서 변경된 부품명을 받는다.
 * 공백 정규화는 compact constructor에서 수행하고, 빈 값은 @NotBlank로 400(INVALID_PARAMETER) 처리한다.
 */
public record ItemNameSyncRequest(
        @NotBlank(message = "itemName은 필수입니다.")
        String itemName
) {
    public ItemNameSyncRequest {
        itemName = itemName == null ? null : itemName.trim();
    }
}
