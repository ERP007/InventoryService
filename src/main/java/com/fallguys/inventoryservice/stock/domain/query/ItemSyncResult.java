package com.fallguys.inventoryservice.stock.domain.query;

import java.util.List;

/**
 * 아이템 마스터 속성(이름/단위/활성) 동기화 결과. sku의 stock 행 일괄 갱신 후 변경 행 수와 변경된 창고 코드 목록을 담는다.
 */
public record ItemSyncResult(
        String sku,
        int updatedCount,
        List<String> warehouseCodes
) {
}
