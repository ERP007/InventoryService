package com.fallguys.inventoryservice.shared.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * 테스트용 JwtDecoder 스텁.
 *
 * <p>운영은 application.yaml의 issuer-uri로 JwtDecoder를 자동 구성하지만, 테스트(@WebMvcTest·@SpringBootTest)에는
 * issuer-uri 설정이 없어 자동 구성되지 않는다. {@link SecurityConfig}의 {@code oauth2ResourceServer.jwt()}가
 * JwtDecoder 빈을 요구하므로 컨텍스트 로딩을 위해 스텁을 제공한다.
 *
 * <p>실제 인증은 SecurityMockMvc의 {@code jwt()} post-processor로 주입하므로 {@code decode}는 호출되지 않는다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            throw new UnsupportedOperationException(
                    "테스트에서는 SecurityMockMvc의 jwt() post-processor로 인증을 주입한다");
        };
    }
}
