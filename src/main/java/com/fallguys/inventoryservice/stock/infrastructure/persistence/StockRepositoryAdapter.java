package com.fallguys.inventoryservice.stock.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.fallguys.inventoryservice.stock.domain.Stock;
import com.fallguys.inventoryservice.stock.domain.StockRepository;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class StockRepositoryAdapter implements StockRepository {

    private final StockJpaDao jpaDao;

    @Override
    public boolean existsBySkuAndWarehouseId(String sku, Long warehouseId) {
        return jpaDao.existsBySkuAndWarehouseId(sku, warehouseId);
    }

    @Override
    public Long save(Stock stock) {
        return jpaDao.save(StockEntity.from(stock)).getId();
    }

    @Override
    public Optional<StockCreateResult> findResultById(Long id) {
        return jpaDao.findResultById(id);
    }
}
