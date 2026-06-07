package com.fallguys.inventoryservice.stock.domain;

import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;

/**
 * [DIP] 재고 이동 이력 영속성 추상화. 도메인이 정의하고 infrastructure가 구현한다.
 */
public interface StockMovementRepository {

    /** 조회 조건(검색·창고필터·유형·기간·정렬·페이지)에 맞는 이동 이력 페이지를 반환한다. 매칭이 없으면 빈 페이지. */
    MovementSummaryPage search(MovementSearchQuery query);
}
