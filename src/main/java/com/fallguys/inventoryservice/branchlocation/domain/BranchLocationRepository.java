package com.fallguys.inventoryservice.branchlocation.domain;

import java.util.List;

/**
 * [DIP] 지점 영속성 추상화. 도메인이 정의하고 infrastructure가 구현한다.
 */
public interface BranchLocationRepository {

    /** 지점명 존재 여부. 등록 전 중복 검사에 사용한다. */
    boolean existsByName(String name);

    /** 식별자 존재 여부. 창고 등록 시 소속 지점 참조 무결성 검사에 사용한다. */
    boolean existsById(Long id);

    /** 지점을 저장하고 식별자가 발급된 도메인을 반환한다. */
    BranchLocation save(BranchLocation branchLocation);

    /** 전체 지점을 id 오름차순으로 반환한다. 없으면 빈 리스트. */
    List<BranchLocation> findAll();

    /**
     * 어느 창고에도 할당되지 않은 지점을 id 오름차순으로 반환한다(창고 등록용, 지점↔창고 1:1). 없으면 빈 리스트.
     * 기본값 빈 리스트는 영속 구현체(BranchLocationRepositoryAdapter)가 반드시 override한다 — 이 조회와 무관한 테스트 stub의 보일러플레이트를 줄이기 위한 기본값일 뿐이다.
     */
    default List<BranchLocation> findUnassigned() {
        return List.of();
    }
}
