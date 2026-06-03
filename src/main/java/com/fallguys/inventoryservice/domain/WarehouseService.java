package com.fallguys.inventoryservice.domain;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fallguys.inventoryservice.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.domain.query.WarehouseSummary;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    /**
     * 창고 목록을 조회한다.
     *
     * 흐름:
     * 1) 검증된 조회 조건(WarehouseSearchQuery)으로 영속성에서 검색한다.
     * 2) 권한(Role)에 따른 응답 분기는 하지 않는다 — 5개 Role 모두 동일 결과를 본다.
     *
     * 트랜잭션: 읽기 전용. 외부 호출 없음.
     */
    @Transactional(readOnly = true)
    public List<WarehouseSummary> search(WarehouseSearchQuery query) {
        return warehouseRepository.search(query);
    }
}
