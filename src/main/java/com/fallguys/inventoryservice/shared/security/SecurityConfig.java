package com.fallguys.inventoryservice.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 리소스 서버(JWT) 보안 설정. 모든 요청은 유효한 JWT를 요구하며(401), Role 기반 인가(403)는
 * 컨트롤러에서 {@link JwtClaimExtractor#requireAnyOf}로 강제한다(엔드포인트별 권한이 달라 선언적 매칭 대신 명시 호출).
 *
 * <p>세션은 STATELESS, CSRF는 토큰 기반 무상태 API라 비활성화한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 인증 없이 접근 가능한 경로: 헬스 체크와 API 문서. */
    private static final String[] PUBLIC_PATHS = {
            "/inventory/health",
            "/actuator/health",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    /**
     * Keycloak 공개키(JWK)로 JWT 서명을 검증하는 디코더.
     * withJwkSetUri는 지연 로딩이라 기동 시 네트워크 호출이 없고, 첫 토큰 검증 시 JWKS를 가져온다.
     * (자동 구성에 의존하지 않고 명시적으로 등록해 컨텍스트 구성을 결정적으로 만든다.)
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:http://localhost:8080/realms/erp/protocol/openid-connect/certs}")
            String jwkSetUri) {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }
}
