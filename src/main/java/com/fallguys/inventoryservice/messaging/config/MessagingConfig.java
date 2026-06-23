package com.fallguys.inventoryservice.messaging.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 비동기 메시징 공통 설정. outbox relay 폴러가 @Scheduled로 동작하도록 스케줄링을 켠다(Slice 2에서 폴러 추가).
 * 스케줄 작업(relay)은 async-enabled가 꺼져 있으면 동작하지 않으므로, 활성 작업이 없을 때 이 설정은 무동작이다.
 */
@Configuration
@EnableScheduling
public class MessagingConfig {
}
