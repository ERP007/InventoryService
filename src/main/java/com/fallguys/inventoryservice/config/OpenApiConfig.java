package com.fallguys.inventoryservice.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearer-jwt";

    /**
     * Swagger "Try it out"이 호출할 외부 베이스 URL.
     * 게이트웨이(/api 접두어) 뒤 배포 환경에서는 OPENAPI_SERVER_URL=https://erp007.xyz/api 로 지정한다.
     * 비어 있으면(로컬) springdoc이 요청 기준으로 server url을 자동 생성한다.
     */
    @Value("${OPENAPI_SERVER_URL:}")
    private String serverUrl;

    @Bean
    public OpenAPI inventoryOpenApi() {
        OpenAPI openApi = new OpenAPI()
                .info(new Info()
                        .title("Inventory Service API")
                        .description("창고 및 재고 이동 이력 서비스 API")
                        .version("v0.0.1"))
                // Authorize 버튼으로 Bearer 토큰을 모든 요청 헤더에 실어 보낸다.
                // 로컬(local 프로파일)에서는 토큰 칸에 Role 문자열(예: ADMIN)을 그대로 입력하면 된다.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));

        // 배포: 외부 베이스 URL을 명시해 Try it out이 같은 origin(/api/inventory/...)으로 호출하게 한다(CORS 방지).
        if (serverUrl != null && !serverUrl.isBlank()) {
            openApi.servers(List.of(new Server().url(serverUrl)));
        }
        return openApi;
    }
}
