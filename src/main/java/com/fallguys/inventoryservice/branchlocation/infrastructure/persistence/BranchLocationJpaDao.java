package com.fallguys.inventoryservice.branchlocation.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BranchLocationJpaDao extends JpaRepository<BranchLocationEntity, Long> {

    /** 지점명 존재 여부. 등록 전 중복 검사에 사용한다. */
    boolean existsByName(String name);

    /**
     * 어느 창고에도 할당되지 않은 지점을 id 오름차순으로 조회한다(창고 등록 모달용).
     * warehouse.branch_id에서 참조되지 않는 지점만 반환한다 — 지점↔창고 1:1이라 미할당만 선택 가능.
     */
    @Query("""
            SELECT b FROM BranchLocationEntity b
            WHERE b.id NOT IN (SELECT w.branchId FROM WarehouseEntity w WHERE w.branchId IS NOT NULL)
            ORDER BY b.id
            """)
    List<BranchLocationEntity> findUnassigned();

}
