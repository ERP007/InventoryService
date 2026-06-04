package com.fallguys.inventoryservice.domain;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fallguys.inventoryservice.domain.command.CreateWarehouseCommand;
import com.fallguys.inventoryservice.domain.exception.BranchNotFoundException;
import com.fallguys.inventoryservice.domain.exception.WarehouseCodeDuplicateException;
import com.fallguys.inventoryservice.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.domain.query.WarehouseSummary;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    // 같은 inventory 서비스 내 다른 애그리거트(BranchLocation) 참조 무결성 확인용
    private final BranchLocationRepository branchLocationRepository;

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

    /**
     * 신규 창고를 등록한다.
     *
     * 흐름:
     * 1) 도메인 모델로 창고를 생성한다 — 유형↔branchId 정합·active=true는 도메인이 보장.
     * 2) DEALER면 소속 지점(branchId)이 실제 존재하는지 확인한다(참조 무결성).
     * 3) 창고 코드 중복 여부를 확인한다(시스템 유일).
     * 4) 저장 후 발급된 id로 읽기 모델(소속 지점명 포함)을 재조회해 반환한다.
     *
     * 트랜잭션: 쓰기. 규칙 위반·중복·참조 오류는 저장 이전에 중단되어 아무것도 저장되지 않는다.
     *
     * 예외:
     * - 유형↔branchId 정합 위반: WarehouseBranchRuleException (400, 도메인 생성 시)
     * - 소속 지점 미존재: BranchNotFoundException (400)
     * - 창고 코드 중복: WarehouseCodeDuplicateException (409)
     */
    @Transactional
    public WarehouseSummary create(CreateWarehouseCommand command) {
        Warehouse warehouse = Warehouse.create(command);

        // branchId 가 없을 때
        if (warehouse.getBranchId() != null && !branchLocationRepository.existsById(warehouse.getBranchId())) {
            throw new BranchNotFoundException(warehouse.getBranchId());
        }

        // WH 코드는 유일해야 한다
        if (warehouseRepository.existsByCode(warehouse.getCode())) {
            throw new WarehouseCodeDuplicateException(warehouse.getCode());
        }
        Long id = warehouseRepository.save(warehouse);

        return warehouseRepository.findSummaryById(id)
                .orElseThrow(() -> new IllegalStateException("저장된 창고를 조회하지 못했습니다: " + id));
    }
}
