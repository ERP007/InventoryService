package com.fallguys.inventoryservice.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소속 지점. 창고와 1:1 매핑되며 목록 조회 시 지점명(branchName) 제공에 사용된다.
 */
@Entity
@Table(name = "branch_location")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BranchLocationEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;
}
