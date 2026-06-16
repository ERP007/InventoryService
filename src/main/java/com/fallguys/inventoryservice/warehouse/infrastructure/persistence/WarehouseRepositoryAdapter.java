package com.fallguys.inventoryservice.warehouse.infrastructure.persistence;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.fallguys.inventoryservice.shared.exception.OptimisticLockConflictException;
import com.fallguys.inventoryservice.warehouse.domain.Warehouse;
import com.fallguys.inventoryservice.warehouse.domain.WarehouseRepository;
import com.fallguys.inventoryservice.shared.query.SortDirection;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseHqSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseOption;
import com.fallguys.inventoryservice.warehouse.domain.command.ChangeWarehouseActiveCommand;
import com.fallguys.inventoryservice.warehouse.domain.command.UpdateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseNotFoundException;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSort;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSortField;
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
    public List<WarehouseHqSummary> findActiveHq() {
        return jpaDao.findActiveHq();
    }

    @Override
    public List<WarehouseOption> findActiveOptions() {
        return jpaDao.findActiveOptions();
    }

    @Override
    public boolean existsByCode(String code) {
        return jpaDao.existsByCode(code);
    }

    @Override
    public boolean existsByBranchIdExcludingCode(Long branchId, String excludeCode) {
        // excludeCode가 null이면(등록) 전체 검사, 있으면(수정) 자기 창고를 제외하고 검사한다.
        return excludeCode == null
                ? jpaDao.existsByBranchId(branchId)
                : jpaDao.existsByBranchIdAndCodeNot(branchId, excludeCode);
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
    public Optional<WarehouseSummaryForEdit> findForEditByCode(String code) {
        return jpaDao.findForEditByCode(code);
    }

    /**
     * 영속 엔티티를 조회해 변경 가능 항목을 갱신한다.
     *
     * 흐름·정합: 창고 코드로 엔티티를 로드(없으면 404)하고, 클라이언트 version과 현재 version을 비교해
     * 다르면 충돌(409)로 막는다(load-modify 방식이라 명시 비교가 분실 갱신 방지의 핵심). 변경 후 flush 시
     * @Version이 한 번 더 동시 수정을 검증하며, 그 실패도 409로 번역한다. 갱신된 읽기 모델로 재조회해 반환한다.
     */
    @Override
    public WarehouseSummaryForEdit update(String code, UpdateWarehouseCommand command) {
        WarehouseEntity entity = jpaDao.findByCode(code)
                .orElseThrow(() -> new WarehouseNotFoundException(code));

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

        // code는 불변이라 갱신 후에도 동일한 코드로 읽기 모델을 재조회한다.
        return jpaDao.findForEditByCode(code)
                .orElseThrow(() -> new IllegalStateException("수정된 창고를 조회하지 못했습니다: " + code));
    }

    /**
     * 영속 엔티티를 조회해 활성 상태를 전환한다(no-op 판정은 호출 전 도메인 서비스가 수행).
     * version 명시 비교로 충돌(409)을 막고, flush 시 @Version이 동시 수정을 한 번 더 검증한다.
     */
    @Override
    public WarehouseSummaryForEdit changeActive(String code, ChangeWarehouseActiveCommand command) {
        WarehouseEntity entity = jpaDao.findByCode(code)
                .orElseThrow(() -> new WarehouseNotFoundException(code));
        if (!Objects.equals(entity.getVersion(), command.version())) {
            throw new OptimisticLockConflictException(
                    "창고가 이미 변경되었습니다. 최신 상태로 재조회 후 다시 시도하세요.");
        }
        entity.changeActive(command.active());
        try {
            jpaDao.saveAndFlush(entity);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new OptimisticLockConflictException(
                    "창고가 이미 변경되었습니다. 최신 상태로 재조회 후 다시 시도하세요.");
        }
        return jpaDao.findForEditByCode(code)
                .orElseThrow(() -> new IllegalStateException("전환된 창고를 조회하지 못했습니다: " + code));
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
        // 소속 지점명(branch)은 조인 테이블(b) 속성이라 JpaSort.unsafe로 별칭(b.name)을 직접 지정한다.
        if (sort.field() == WarehouseSortField.BRANCH) {
            return JpaSort.unsafe(direction, "b.name");
        }
        return Sort.by(direction, sort.field().property());
    }
}
