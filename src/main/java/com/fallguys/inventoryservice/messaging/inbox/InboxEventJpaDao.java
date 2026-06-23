package com.fallguys.inventoryservice.messaging.inbox;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * inbox 영속 인터페이스(Spring Data). 중복 수신 판정(existsById)과 상태 갱신에 사용한다(Slice 3).
 */
public interface InboxEventJpaDao extends JpaRepository<InboxEvent, UUID> {
}
