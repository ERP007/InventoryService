package com.fallguys.inventoryservice.controller.dto;

import java.util.List;

import com.fallguys.inventoryservice.domain.BranchLocation;

/**
 * 지점 목록 조회 응답. 드롭다운 채움 용도라 페이지네이션·정렬 메타가 없다.
 *
 * @param content 지점 항목 목록(0건이면 빈 배열)
 */
public record BranchLocationListResponse(
        List<BranchLocationResponse> content
) {

    public static BranchLocationListResponse from(List<BranchLocation> branchLocations) {
        List<BranchLocationResponse> content = branchLocations.stream()
                .map(BranchLocationResponse::from)
                .toList();
        return new BranchLocationListResponse(content);
    }
}
