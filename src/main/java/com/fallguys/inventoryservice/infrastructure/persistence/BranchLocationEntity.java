package com.fallguys.inventoryservice.infrastructure.persistence;

import com.fallguys.inventoryservice.domain.BranchLocation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소속 지점. 창고와 1:1 매핑되며 목록 조회 시 지점명(branchName) 제공에 사용된다.
 * ex) "창고명" : "서울 1창고", "소속 지점" : "서울 강남 지점" 의 BRANCH_MANAGER 김철수, BRANCH_STAFF 김영희
 *
 */
@Entity
@Table(name = "branch_location")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchLocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    private BranchLocationEntity(String name) {
        this.name = name;
    }

    /** 신규 지점 도메인을 영속 엔티티로 변환한다(id는 DB가 발급하므로 설정하지 않는다). */
    public static BranchLocationEntity from(BranchLocation branchLocation) {
        return new BranchLocationEntity(branchLocation.getName());
    }

    /** 영속 엔티티를 도메인 모델로 변환한다(저장 후 반환·조회). */
    public BranchLocation toDomain() {
        return BranchLocation.of(id, name);
    }
}
