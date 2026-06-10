package com.fallguys.inventoryservice.stock.domain;

import java.time.Instant;
import java.util.List;

import com.fallguys.inventoryservice.stock.domain.query.InboundMovement;
import com.fallguys.inventoryservice.stock.domain.query.MovementHistory;
import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;
import com.fallguys.inventoryservice.stock.domain.query.OutboundMovement;

/**
 * [DIP] 재고 이동 이력 영속성 추상화. 도메인이 정의하고 infrastructure가 구현한다.
 */
public interface StockMovementRepository {

    /** 조회 조건(검색·창고필터·유형·기간·정렬·페이지)에 맞는 이동 이력 페이지를 반환한다. 매칭이 없으면 빈 페이지. */
    MovementSummaryPage search(MovementSearchQuery query);

    /** sku의 최근 이동 이력을 limit건 조회한다(상세 패널). warehouseCodes가 비어있으면 전체, 있으면 해당 창고로 한정. */
    List<MovementHistory> findRecentBySku(String sku, List<String> warehouseCodes, int limit);

    /** since 이후 이동 건수를 센다(KPI 최근 7일). warehouseCodes가 비어있으면 전사. */
    long countRecent(List<String> warehouseCodes, Instant since);

    /** 신규 이동 이력을 저장하고 식별자·발생시각이 채워진 결과를 반환한다(append-only). */
    StockMovement save(StockMovement movement);

    /**
     * (sourceRef × 창고)로 이미 적재된 INBOUND 이동 이력을 조회한다(입고 멱등 replay용). 없으면 빈 리스트.
     */
    List<InboundMovement> findInboundBySourceRefAndWarehouseCode(String sourceRef, String warehouseCode);

    /**
     * (sourceRef × 창고)로 이미 적재된 OUTBOUND 이동 이력을 조회한다(출고 멱등 replay용). 없으면 빈 리스트.
     */
    List<OutboundMovement> findOutboundBySourceRefAndWarehouseCode(String sourceRef, String warehouseCode);
}
