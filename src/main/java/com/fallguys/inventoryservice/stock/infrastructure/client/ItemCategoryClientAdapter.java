package com.fallguys.inventoryservice.stock.infrastructure.client;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fallguys.inventoryservice.stock.domain.ItemCategoryProvider;
import com.fallguys.inventoryservice.stock.domain.exception.ItemServiceUnavailableException;
import com.fallguys.inventoryservice.stock.domain.query.ItemCategory;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * ItemCategoryProvider 구현체. Item 서비스의 {@code GET /internal/items/{sku}}를 호출해 대분류·중분류만 추출한다.
 * (name·unit 등 나머지 필드는 stock 스냅샷을 쓰므로 무시한다.)
 *
 * <p>게이트: {@code item.integration.enabled=false}(기본)이면 호출하지 않고 {@code Optional.empty()}를 반환한다
 * — Item 미배포 동안 매 조회마다 타임아웃을 먹지 않기 위함. 배포 후 true로 전환한다.
 *
 * <p>에러 번역(§10): Item 404 → {@code Optional.empty()}(부품 없음, graceful) /
 * 그 외 기술 실패(연결·타임아웃·4xx/5xx) → {@link ItemServiceUnavailableException}.
 */
@Component
public class ItemCategoryClientAdapter implements ItemCategoryProvider {

    private final boolean enabled;
    private final String baseUrl;
    private final RestClient restClient;

    public ItemCategoryClientAdapter(
            @Value("${item.integration.enabled:false}") boolean enabled,
            @Value("${item.base-url:}") String baseUrl) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        // Spring Boot 자동구성 빈(RestClient.Builder)에 의존하지 않고 기본 클라이언트를 직접 생성한다(컨텍스트 결정성).
        this.restClient = RestClient.create();
    }

    @Override
    public Optional<ItemCategory> findCategoryBySku(String sku) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            ItemInfoResponse response = restClient.get()
                    .uri(baseUrl + "/internal/items/{sku}", sku)
                    .retrieve()
                    .body(ItemInfoResponse.class);
            if (response == null) {
                return Optional.empty();
            }
            return Optional.of(new ItemCategory(response.majorCategory(), response.middleCategory()));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty(); // Item에 부품 없음 → 카테고리 없음(graceful)
            }
            throw new ItemServiceUnavailableException(
                    "Item 부품 조회 실패(HTTP " + e.getStatusCode().value() + "): sku=" + sku, e);
        } catch (RestClientException e) {
            throw new ItemServiceUnavailableException("Item 부품 조회 실패(연결/타임아웃): sku=" + sku, e);
        }
    }

    /**
     * Item {@code GET /internal/items/{sku}} 응답 중 우리가 쓰는 분류 필드만 매핑한다.
     * 나머지 필드(name·unit·unitPrice·safetyStock·active 등)는 ignoreUnknown으로 무시한다
     * (ObjectMapper 전역 설정에 의존하지 않고 항상 무시 보장).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ItemInfoResponse(String majorCategory, String middleCategory) {
    }
}
