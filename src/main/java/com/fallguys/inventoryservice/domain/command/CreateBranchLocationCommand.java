package com.fallguys.inventoryservice.domain.command;

/**
 * 지점 등록 유스케이스 입력. 상태 변경이므로 Command record로 받는다
 *
 * @param name 등록할 지점명(표현 계층에서 trim·형식 검증을 통과한 값)
 */
public record CreateBranchLocationCommand(String name) {
}
