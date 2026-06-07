package com.fallguys.inventoryservice.stock.infrastructure.persistence;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Repository;

import com.fallguys.inventoryservice.shared.query.SortDirection;
import com.fallguys.inventoryservice.stock.domain.StockMovementRepository;
import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSortField;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummary;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class StockMovementRepositoryAdapter implements StockMovementRepository {

    // 업무 시간대(KST). from/to(일자)를 [그 날 00:00, 종료일+1일 00:00) Instant 구간으로 변환할 때 사용한다.
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final StockMovementJpaDao jpaDao;

    @Override
    public MovementSummaryPage search(MovementSearchQuery query) {
        Pageable pageable = PageRequest.of(query.page() - 1, query.size(),
                toSort(query.sortField(), query.sortDirection()));
        Instant fromInstant = query.from().atStartOfDay(ZONE).toInstant();
        Instant toExclusive = query.to().plusDays(1).atStartOfDay(ZONE).toInstant();
        Page<MovementSummary> page = jpaDao.search(
                toLikePattern(query.keyword()),
                query.hasWarehouseFilter(),
                query.warehouseCodes(),
                query.type(),
                fromInstant,
                toExclusive,
                pageable);
        return new MovementSummaryPage(
                page.getContent(), query.page(), query.size(), page.getTotalElements(), page.getTotalPages());
    }

    /**
     * 도메인 정렬 조건을 ORDER BY 식으로 변환한다(기술 의존은 이 계층에 한정).
     * 안정 정렬을 위해 id 오름차순을 tie-breaker로 덧붙인다.
     */
    private Sort toSort(MovementSortField field, SortDirection direction) {
        Sort.Direction springDirection = direction == SortDirection.DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
        String expression = switch (field) {
            case OCCURRED_AT -> "m.performedAt";
            case DELTA -> "m.delta";
        };
        return JpaSort.unsafe(springDirection, expression)
                .and(JpaSort.unsafe(Sort.Direction.ASC, "m.id"));
    }

    /** 부분 일치 LIKE 패턴으로 변환한다(소문자화 + 양끝 와일드카드). 검색어가 없으면 null. */
    private String toLikePattern(String keyword) {
        if (keyword == null) {
            return null;
        }
        return "%" + keyword.toLowerCase(Locale.ROOT) + "%";
    }
}
