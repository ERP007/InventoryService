package com.fallguys.inventoryservice.stock.domain;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fallguys.inventoryservice.stock.domain.query.ItemSyncResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockItemSyncService {

    private final StockRepository stockRepository;

    /**
     * Item 마스터의 부품명 변경을 stock 비정규화 컬럼에 동기화한다(internal). 해당 sku의 모든 창고 행을 일괄 갱신한다.
     *
     * 흐름:
     * 1) sku로 갱신 대상 창고 코드를 조회한다(code 오름차순, 창고 활성 여부 무관).
     * 2) sku를 가진 모든 stock 행의 item_name을 일괄 UPDATE한다(절대값 교체).
     *
     * 트랜잭션: 쓰기. sku 존재·창고 활성 검증은 하지 않으며, 대상 행이 없으면 0건으로 정상 종료한다.
     *  재고 이력(stock_movement)은 변경하지 않고, @Version·감사 컬럼도 건드리지 않는다(수량 변화가 아닌 마스터 미러 동기화 — last-write-wins).
     *
     * 예외: 없음(검증 실패는 표현 계층의 @Valid가, 인가는 컨트롤러가 담당).
     */
    @Transactional
    public ItemSyncResult syncItemName(String sku, String itemName) {
        List<String> warehouseCodes = stockRepository.findWarehouseCodesBySku(sku);
        int updatedCount = stockRepository.updateItemNameBySku(sku, itemName);
        return new ItemSyncResult(sku, updatedCount, warehouseCodes);
    }

    /**
     * Item 마스터의 단위 변경을 stock 비정규화 컬럼에 동기화한다(internal). 흐름·트랜잭션·동시성 정책은 {@link #syncItemName}과 동일하다.
     * 해당 sku의 모든 창고 행 item_unit을 일괄 교체하고, 대상 행이 없으면 0건으로 정상 종료한다(재고 이력·@Version·감사 컬럼 미반영).
     */
    @Transactional
    public ItemSyncResult syncItemUnit(String sku, ItemUnit itemUnit) {
        List<String> warehouseCodes = stockRepository.findWarehouseCodesBySku(sku);
        int updatedCount = stockRepository.updateItemUnitBySku(sku, itemUnit);
        return new ItemSyncResult(sku, updatedCount, warehouseCodes);
    }
}
