package com.fallguys.inventoryservice.infrastructure.persistence;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import com.fallguys.inventoryservice.domain.WarehouseRepository;
import com.fallguys.inventoryservice.domain.query.SortDirection;
import com.fallguys.inventoryservice.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.domain.query.WarehouseSort;
import com.fallguys.inventoryservice.domain.query.WarehouseSummary;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class WarehouseRepositoryAdapter implements WarehouseRepository {

    private final WarehouseJpaDao jpaDao;

    @Override
    public List<WarehouseSummary> search(WarehouseSearchQuery query) {
        Sort sort = toSpringSort(query.sort());
        return jpaDao.search(toLikePattern(query.keyword()), query.type(), query.status().toActiveFilter(), sort);
    }

    /** 부분 일치 LIKE 패턴으로 변환한다(소문자화 + 양끝 와일드카드). 검색어가 없으면 null. */
    private String toLikePattern(String keyword) {
        if (keyword == null) {
            return null;
        }
        return "%" + keyword.toLowerCase(Locale.ROOT) + "%";
    }

    /** 도메인 정렬 조건을 Spring Data Sort로 변환한다(기술 의존은 이 계층에 한정). */
    private Sort toSpringSort(WarehouseSort sort) {
        Sort.Direction direction = sort.direction() == SortDirection.DESC
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, sort.field().property());
    }
}
