package com.fallguys.inventoryservice.stock.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Repository;

import com.fallguys.inventoryservice.shared.query.SortDirection;
import com.fallguys.inventoryservice.stock.domain.MovementType;
import com.fallguys.inventoryservice.stock.domain.StockMovement;
import com.fallguys.inventoryservice.stock.domain.StockMovementRepository;
import com.fallguys.inventoryservice.stock.domain.query.DailyMovementCount;
import com.fallguys.inventoryservice.stock.domain.query.InboundMovement;
import com.fallguys.inventoryservice.stock.domain.query.MovementHistory;
import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSortField;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummary;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;
import com.fallguys.inventoryservice.stock.domain.query.MovementTypeAt;
import com.fallguys.inventoryservice.stock.domain.query.OutboundMovement;

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

    @Override
    public List<MovementHistory> findRecentBySku(String sku, List<String> warehouseCodes, int limit) {
        return jpaDao.findRecentBySku(sku, !warehouseCodes.isEmpty(), warehouseCodes, PageRequest.of(0, limit));
    }

    @Override
    public long countRecent(List<String> warehouseCodes, Instant since) {
        return jpaDao.countRecent(!warehouseCodes.isEmpty(), warehouseCodes, since);
    }

    /**
     * 기간 [from 00:00, to+1일 00:00) KST의 이동을 (일자 × 유형) 건수로 집계한다.
     * DB의 시간대 변환 함수에 의존하지 않도록 raw 행(performedAt, type)을 받아 KST 일자로 변환·그룹한다(H2/PostgreSQL 호환).
     */
    @Override
    public List<DailyMovementCount> countDailyByType(List<String> warehouseCodes, LocalDate from, LocalDate to) {
        Instant fromInstant = from.atStartOfDay(ZONE).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(ZONE).toInstant();
        List<MovementTypeAt> rows = jpaDao.findTypeAndPerformedAt(
                !warehouseCodes.isEmpty(), warehouseCodes, fromInstant, toExclusive);

        Map<DayType, Long> grouped = new HashMap<>();
        for (MovementTypeAt row : rows) {
            LocalDate date = row.performedAt().atZone(ZONE).toLocalDate();
            grouped.merge(new DayType(date, row.type()), 1L, Long::sum);
        }
        return grouped.entrySet().stream()
                .map(entry -> new DailyMovementCount(entry.getKey().date(), entry.getKey().type(), entry.getValue()))
                .toList();
    }

    /** (일자, 유형) 그룹 키. */
    private record DayType(LocalDate date, MovementType type) {}

    @Override
    public StockMovement save(StockMovement movement) {
        return jpaDao.save(StockMovementEntity.from(movement)).toDomain();
    }

    @Override
    public List<InboundMovement> findInboundBySourceRefAndWarehouseCode(String sourceRef, String warehouseCode) {
        return jpaDao.findInboundBySourceRefAndWarehouseCode(sourceRef, warehouseCode);
    }

    @Override
    public List<OutboundMovement> findOutboundBySourceRefAndWarehouseCode(String sourceRef, String warehouseCode) {
        return jpaDao.findOutboundBySourceRefAndWarehouseCode(sourceRef, warehouseCode);
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
