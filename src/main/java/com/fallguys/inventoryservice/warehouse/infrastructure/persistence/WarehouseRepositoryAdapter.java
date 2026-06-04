package com.fallguys.inventoryservice.warehouse.infrastructure.persistence;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.fallguys.inventoryservice.shared.exception.OptimisticLockConflictException;
import com.fallguys.inventoryservice.warehouse.domain.Warehouse;
import com.fallguys.inventoryservice.warehouse.domain.WarehouseRepository;
import com.fallguys.inventoryservice.shared.query.SortDirection;
import com.fallguys.inventoryservice.warehouse.domain.command.UpdateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseNotFoundException;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSort;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummary;

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

    @Override
    public boolean existsByCode(String code) {
        return jpaDao.existsByCode(code);
    }

    @Override
    public Long save(Warehouse warehouse) {
        WarehouseEntity saved = jpaDao.save(WarehouseEntity.from(warehouse));
        return saved.getId();
    }

    @Override
    public Optional<WarehouseSummary> findSummaryById(Long id) {
        return jpaDao.findSummaryById(id);
    }

    @Override
    public Optional<WarehouseSummaryForEdit> findForEditById(Long id) {
        return jpaDao.findForEditById(id);
    }

    /**
     * 영속 엔티티를 조회해 변경 가능 항목을 갱신한다.
     *
     * 흐름·정합: 식별자로 엔티티를 로드(없으면 404)하고, 클라이언트 version과 현재 version을 비교해
     * 다르면 충돌(409)로 막는다(load-modify 방식이라 명시 비교가 분실 갱신 방지의 핵심). 변경 후 flush 시
     * @Version이 한 번 더 동시 수정을 검증하며, 그 실패도 409로 번역한다. 갱신된 읽기 모델로 재조회해 반환한다.
     */
    @Override
    public WarehouseSummaryForEdit update(Long id, UpdateWarehouseCommand command) {
        WarehouseEntity entity = jpaDao.findById(id)
                .orElseThrow(() -> new WarehouseNotFoundException(id));

        // version 이 다르면 수정 불가 - 낙관적 락
        if (!Objects.equals(entity.getVersion(), command.version())) {
            throw new OptimisticLockConflictException(
                    "창고가 이미 변경되었습니다. 최신 상태로 재조회 후 다시 시도하세요.");
        }

        entity.update(command);

        try {
            jpaDao.saveAndFlush(entity);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new OptimisticLockConflictException(
                    "창고가 이미 변경되었습니다. 최신 상태로 재조회 후 다시 시도하세요.");
        }

        return jpaDao.findForEditById(id)
                .orElseThrow(() -> new IllegalStateException("수정된 창고를 조회하지 못했습니다: " + id));
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
