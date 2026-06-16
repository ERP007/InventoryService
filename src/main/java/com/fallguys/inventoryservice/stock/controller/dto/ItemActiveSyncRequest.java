package com.fallguys.inventoryservice.stock.controller.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 아이템 활성 여부 동기화 요청(internal). Item 마스터에서 토글된 활성 상태를 받는다.
 * 누락 시 @NotNull로 400, boolean이 아닌 값은 역직렬화 단계에서 400으로 처리된다(Boolean으로 받아 null과 미지정을 구분).
 */
public record ItemActiveSyncRequest(
        @NotNull(message = "active는 필수입니다.")
        Boolean active
) {
}
