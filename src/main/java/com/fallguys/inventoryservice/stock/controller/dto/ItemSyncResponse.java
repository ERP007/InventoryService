package com.fallguys.inventoryservice.stock.controller.dto;

import java.util.List;

import com.fallguys.inventoryservice.stock.domain.query.ItemSyncResult;

/**
 * 아이템 속성 동기화 응답(internal). 변경된 stock 행 수와 변경된 창고 코드 목록을 반환한다.
 */
public record ItemSyncResponse(
        String sku,
        int updatedCount,
        List<String> warehouseCodes
) {

    public static ItemSyncResponse from(ItemSyncResult result) {
        return new ItemSyncResponse(result.sku(), result.updatedCount(), result.warehouseCodes());
    }
}
