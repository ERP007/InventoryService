package com.fallguys.inventoryservice.shared.activity;

/**
 * 사용자 활동 로그 이벤트 발행 포트(DIP). 업무 트랜잭션이 성공적으로 저장된 뒤, 같은 트랜잭션에서 호출해
 * 활동 이벤트를 outbox에 적재한다(발행은 relay가 커밋 이후 수행). 구현은 infrastructure(messaging)에 둔다.
 *
 * <p>수행자 사번(employeeNo)은 구현체가 현재 인증 컨텍스트(JWT)에서 직접 가져오므로 호출자는 표시용 필드만 넘긴다.
 *
 * @param action  활동 종류(배지 매핑용)
 * @param title   주요 문구(진한 글씨). 예: 창고 이름, 부품 이름
 * @param content 상세 내용(회색 작은 글씨). 예: 창고 코드, sku. 없으면 null
 * @param status  배지 보조 문구. 예: 재고/안전재고 조정 "-3"·"+7", 창고 상태 "active"/"inactive". 없으면 null
 */
public interface UserActivityRecorder {
    void record(UserActivityAction action, String title, String content, String status);
}
