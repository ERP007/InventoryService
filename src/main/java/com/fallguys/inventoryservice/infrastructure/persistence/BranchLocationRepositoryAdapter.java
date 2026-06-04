package com.fallguys.inventoryservice.infrastructure.persistence;

import org.springframework.stereotype.Repository;

import com.fallguys.inventoryservice.domain.BranchLocation;
import com.fallguys.inventoryservice.domain.BranchLocationRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class BranchLocationRepositoryAdapter implements BranchLocationRepository {

    private final BranchLocationJpaDao jpaDao;

    @Override
    public boolean existsByName(String name) {
        return jpaDao.existsByName(name);
    }

    @Override
    public BranchLocation save(BranchLocation branchLocation) {
        BranchLocationEntity saved = jpaDao.save(BranchLocationEntity.from(branchLocation));
        return saved.toDomain();
    }
}
