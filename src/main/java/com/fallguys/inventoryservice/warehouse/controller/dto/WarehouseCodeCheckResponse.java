package com.fallguys.inventoryservice.warehouse.controller.dto;

/**
 * 창고 코드 중복 확인 응답. available=true면 등록 가능(미사용), false면 이미 존재한다.
 * code는 검증·정규화(trim)를 거친 확인 대상 코드를 그대로 돌려준다(프론트의 입력값↔응답 정합 비교용).
 */
public record WarehouseCodeCheckResponse(String code, boolean available) {
}
