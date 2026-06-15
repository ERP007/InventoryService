package com.fallguys.inventoryservice.warehouse.domain;

import com.fallguys.inventoryservice.branchlocation.domain.BranchLocationRepository;

import java.util.List;

import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fallguys.inventoryservice.warehouse.domain.command.ChangeWarehouseActiveCommand;
import com.fallguys.inventoryservice.warehouse.domain.command.CreateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.command.UpdateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.exception.BranchAlreadyAssignedException;
import com.fallguys.inventoryservice.warehouse.domain.exception.BranchNotFoundException;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseCodeDuplicateException;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseNotFoundException;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseHqSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummary;

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
     * 출고 창고 선택 드롭다운용으로 활성(active=true) 본사(type=HQ) 창고를 슬림 모델로 조회한다.
     *
     * 흐름: 영속성에서 활성·HQ 조건으로 필터링된 목록을 code 오름차순으로 가져온다.
     * 트랜잭션: 읽기 전용. 외부 호출 없음. 권한 분기 없음(전 Role 동일 결과). 매칭 0건이면 빈 리스트.
     */
    @Transactional(readOnly = true)
    public List<WarehouseHqSummary> findHqWarehouses() {
        return warehouseRepository.findActiveHq();
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
     * - 소속 지점 중복 할당(1:1 위반): BranchAlreadyAssignedException (409)
     * - 창고 코드 중복: WarehouseCodeDuplicateException (409)
     */
    @Transactional
    public WarehouseSummary create(CreateWarehouseCommand command) {
        Warehouse warehouse = Warehouse.create(command);

        // DEALER는 소속 지점이 (1) 실재하고 (2) 다른 창고에 아직 할당되지 않아야 한다(지점↔창고 1:1 매핑).
        if (warehouse.getBranchId() != null) {
            if (!branchLocationRepository.existsById(warehouse.getBranchId())) {
                throw new BranchNotFoundException(warehouse.getBranchId());
            }
            if (warehouseRepository.existsByBranchIdExcludingCode(warehouse.getBranchId(), null)) {
                throw new BranchAlreadyAssignedException(warehouse.getBranchId());
            }
        }

        // WH 코드는 유일해야 한다
        if (warehouseRepository.existsByCode(warehouse.getCode())) {
            throw new WarehouseCodeDuplicateException(warehouse.getCode());
        }
        Long id = warehouseRepository.save(warehouse);

        return warehouseRepository.findSummaryById(id)
                .orElseThrow(() -> new IllegalStateException("저장된 창고를 조회하지 못했습니다: " + id));
    }

    /**
     * 수정 모달 프리필을 위해 창고 단건을 상세 조회한다(소속 지점명·주소·version 포함).
     *
     * 흐름:
     * 1) 창고 코드로 읽기 모델(WarehouseSummaryForEdit)을 조회한다.
     * 2) 없으면 존재를 은닉하여 404로 막는다("없음"과 "소속 외"를 구분하지 않는다).
     *
     * 트랜잭션: 읽기 전용. 외부 호출 없음.
     *
     * 예외:
     * - 창고 없음/소속 외: WarehouseNotFoundException (404)
     */
    @Transactional(readOnly = true)
    public WarehouseSummaryForEdit getByCode(String code) {
        return warehouseRepository.findForEditByCode(code)
                .orElseThrow(() -> new WarehouseNotFoundException(code));
    }

    /**
     * 창고의 변경 가능 항목(name·type·branchId·address)을 수정한다. code는 불변(Command가 거부)이다.
     *
     * 흐름:
     * 1) 유형↔branchId 정합을 도메인 불변식으로 검증한다(DB 접근 없음).
     * 2) DEALER면 소속 지점(branchId)이 실제 존재하는지 확인한다(참조 무결성).
     * 3) 영속성에 위임해 수정한다 — 창고 없음(404)·version 충돌(409)은 이 단계에서 판정된다.
     *
     * 트랜잭션: 쓰기. 검증·참조·충돌 실패 시 변경 없이 롤백된다.
     *
     * 예외:
     * - 유형↔branchId 정합 위반: WarehouseBranchRuleException (400)
     * - 소속 지점 미존재: BranchNotFoundException (400)
     * - 소속 지점 중복 할당(1:1 위반): BranchAlreadyAssignedException (409)
     * - 창고 없음/소속 외: WarehouseNotFoundException (404)
     * - version 불일치(동시 수정): OptimisticLockConflictException (409)
     */
    @Transactional
    public WarehouseSummaryForEdit update(String code, UpdateWarehouseCommand command) {
        Warehouse.validateBranchRule(command.type(), command.branchId());
        if (command.branchId() != null) {
            if (!branchLocationRepository.existsById(command.branchId())) {
                throw new BranchNotFoundException(command.branchId());
            }
            // 자기 자신을 제외하고 다른 창고가 같은 지점을 쓰면 1:1 위반.
            if (warehouseRepository.existsByBranchIdExcludingCode(command.branchId(), code)) {
                throw new BranchAlreadyAssignedException(command.branchId());
            }
        }
        return warehouseRepository.update(code, command);
    }

    /**
     * 창고 활성 상태를 전환한다. 같은 값으로의 전환은 멱등(no-op)이라 변경 없이 현재 상태를 반환한다.
     *
     * 흐름:
     * 1) 창고 코드로 현재 상태를 조회한다(없으면 404).
     * 2) 요청 active가 현재와 같으면 no-op으로 현재 상태를 그대로 반환한다(version 비교 없음 — 멱등·재시도 안전).
     * 3) 다르면 영속성에 위임해 전환한다 — version 불일치는 409.
     *
     * 트랜잭션: 쓰기. no-op이면 사실상 읽기만 수행하고 version은 증가하지 않는다.
     *
     * 예외:
     * - 창고 없음/소속 외: WarehouseNotFoundException (404)
     * - version 불일치(실제 전환 시): OptimisticLockConflictException (409)
     */
    @Transactional
    public WarehouseSummaryForEdit changeActive(String code, ChangeWarehouseActiveCommand command) {
        WarehouseSummaryForEdit current = warehouseRepository.findForEditByCode(code)
                .orElseThrow(() -> new WarehouseNotFoundException(code));
        if (current.active() == command.active()) {
            return current;
        }
        return warehouseRepository.changeActive(code, command);
    }
}
