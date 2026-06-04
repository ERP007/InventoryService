package com.fallguys.inventoryservice.branchlocation.controller.dto;

import com.fallguys.inventoryservice.branchlocation.domain.BranchLocation;

/**
 * 지점 등록 응답. 발급된 id와 등록된 name을 반환한다.
 */
public record BranchLocationResponse(
        Long id,
        String name
) {

    public static BranchLocationResponse from(BranchLocation branchLocation) {
        return new BranchLocationResponse(branchLocation.getId(), branchLocation.getName());
    }
}
