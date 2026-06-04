package com.fallguys.inventoryservice.domain;

import lombok.Getter;

/**
 * 소속 지점 마스터. 식별자(id)로 동등성이 결정되는 도메인 엔티티이며 JPA에 의존하지 않는다.
 * 컬럼이 id + name뿐이라 상태 전이는 없으나, 마스터 식별 대상이므로 VO가 아닌 엔티티(class)로 둔다.
 */
@Getter
public class BranchLocation {

    private final Long id;
    private final String name;

    private BranchLocation(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * 신규 지점을 생성한다. id는 영속 시 발급되므로 null이다.
     * 불변식: name은 null/공백이 아니어야 한다(표현 계층 @Valid가 1차 검증하나 도메인이 최종 방어).
     */
    public static BranchLocation create(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("지점명은 필수입니다.");
        }
        return new BranchLocation(null, name);
    }

    /** 영속 데이터로부터 도메인을 복원한다(이미 저장된 유효 데이터이므로 불변식 검증을 생략한다). */
    public static BranchLocation of(Long id, String name) {
        return new BranchLocation(id, name);
    }
}
