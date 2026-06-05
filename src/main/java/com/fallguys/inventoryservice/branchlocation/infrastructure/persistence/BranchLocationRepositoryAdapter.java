package com.fallguys.inventoryservice.branchlocation.infrastructure.persistence;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import com.fallguys.inventoryservice.branchlocation.domain.BranchLocation;
import com.fallguys.inventoryservice.branchlocation.domain.BranchLocationRepository;

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
    public boolean existsById(Long id) {
        return jpaDao.existsById(id);
    }

    @Override
    public BranchLocation save(BranchLocation branchLocation) {
        BranchLocationEntity saved = jpaDao.save(BranchLocationEntity.from(branchLocation));
        return saved.toDomain();
    }

    @Override
    public List<BranchLocation> findAll() {
        return jpaDao.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(BranchLocationEntity::toDomain)
                .toList();
    }
}
