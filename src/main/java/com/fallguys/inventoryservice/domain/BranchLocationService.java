package com.fallguys.inventoryservice.domain;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fallguys.inventoryservice.domain.command.CreateBranchLocationCommand;
import com.fallguys.inventoryservice.domain.exception.BranchLocationNameDuplicateException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BranchLocationService {

    private final BranchLocationRepository branchLocationRepository;

    /**
     * 신규 지점을 등록한다.
     *
     * 흐름:
     * 1) 지점명 중복 여부를 확인한다(시스템 내 유일 보장).
     * 2) 도메인 모델로 지점을 생성한다(불변식 검증은 도메인이 수행).
     * 3) 저장하고 식별자가 발급된 도메인을 반환한다.
     *
     * 트랜잭션: 쓰기. 중복이 발견되면 생성·저장 이전에 중단되어 아무것도 저장되지 않는다.
     *
     * 예외:
     * - 지점명 중복: BranchLocationNameDuplicateException (409 매핑, 롤백)
     */
    @Transactional
    public BranchLocation create(CreateBranchLocationCommand command) {
        if (branchLocationRepository.existsByName(command.name())) {
            throw new BranchLocationNameDuplicateException(command.name());
        }
        BranchLocation branchLocation = BranchLocation.create(command.name());
        return branchLocationRepository.save(branchLocation);
    }
}
