package com.fallguys.inventoryservice.branchlocation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchLocationJpaDao extends JpaRepository<BranchLocationEntity, Long> {

    /** 지점명 존재 여부. 등록 전 중복 검사에 사용한다. */
    boolean existsByName(String name);

}
