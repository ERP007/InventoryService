package com.fallguys.inventoryservice.messaging.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fallguys.inventoryservice.stock.domain.ItemUnit;

/**
 * 품목 마스터 스냅샷 변경 이벤트의 payload(item-service → inventory). 이름·단위·활성 여부를 한 번에 싣는 전체 스냅샷이다.
 * itemUnit은 enum(EA/BOX/SET/L) — 잘못된 값이면 역직렬화 단계에서 실패해 malformed로 DLQ.
 * itemUpdatedAt은 순서 역전 가드용 후보이나 V1에선 사용하지 않는다(eventId inbox 중복 방지 + last-write-wins).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemSnapshotPayload(
        String sku,
        String itemName,
        ItemUnit itemUnit,
        Boolean active,
        String itemUpdatedAt
) {

    /** 계약 검증. 위반은 재시도 불가한 형식 오류이므로 MalformedEventException으로 던져 DLQ로 보낸다. */
    public void validate() {
        if (isBlank(sku) || isBlank(itemName) || itemUnit == null || active == null) {
            throw new MalformedEventException("item snapshot 필수 필드 누락(sku·itemName·itemUnit·active)");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
