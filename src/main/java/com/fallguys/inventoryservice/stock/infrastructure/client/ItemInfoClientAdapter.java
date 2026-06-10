package com.fallguys.inventoryservice.stock.infrastructure.client;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fallguys.inventoryservice.stock.domain.ItemInfoProvider;
import com.fallguys.inventoryservice.stock.domain.ItemUnit;
import com.fallguys.inventoryservice.stock.domain.exception.ItemServiceUnavailableException;
import com.fallguys.inventoryservice.stock.domain.query.ItemInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * ItemInfoProvider 구현체. Item 서비스의 {@code GET /internal/items/{sku}}를 호출해 부품 정보(이름·단위·분류·기본 안전재고)를 추출한다.
 *
 * <p>게이트: {@code item.integration.enabled=false}(기본)이면 호출하지 않고 {@code Optional.empty()}를 반환한다
 * — Item 미배포 동안 매 호출마다 타임아웃을 먹지 않기 위함. 배포 후 true로 전환한다.
 *
 * <p>에러 번역(§10): Item 404 → {@code Optional.empty()}(부품 없음) /
 * 그 외 기술 실패(연결·타임아웃·4xx/5xx·단위 계약 불일치) → {@link ItemServiceUnavailableException}.
 */
@Component
public class ItemInfoClientAdapter implements ItemInfoProvider {

    private final boolean enabled;
    private final String baseUrl;
    private final RestClient restClient;

    public ItemInfoClientAdapter(
            @Value("${item.integration.enabled:false}") boolean enabled,
            @Value("${item.base-url:}") String baseUrl) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        // Spring Boot 자동구성 빈(RestClient.Builder)에 의존하지 않고 기본 클라이언트를 직접 생성한다(컨텍스트 결정성).
        this.restClient = RestClient.create();
    }

    @Override
    public Optional<ItemInfo> findBySku(String sku) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            ItemResponse response = restClient.get()
                    .uri(baseUrl + "/internal/items/{sku}", sku)
                    .retrieve()
                    .body(ItemResponse.class);
            if (response == null) {
                return Optional.empty();
            }
            return Optional.of(new ItemInfo(
                    response.name(), parseUnit(response.unit(), sku),
                    response.majorCategory(), response.middleCategory(), response.safetyStock()));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty(); // Item에 부품 없음
            }
            throw new ItemServiceUnavailableException(
                    "Item 부품 조회 실패(HTTP " + e.getStatusCode().value() + "): sku=" + sku, e);
        } catch (RestClientException e) {
            throw new ItemServiceUnavailableException("Item 부품 조회 실패(연결/타임아웃): sku=" + sku, e);
        }
    }

    /** Item 단위 문자열을 ItemUnit으로 해석한다. 우리 enum(EA/BOX/SET/L) 밖이면 계약 불일치로 호출 실패 처리한다. */
    private static ItemUnit parseUnit(String unit, String sku) {
        try {
            return ItemUnit.valueOf(unit);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ItemServiceUnavailableException("Item 단위를 해석할 수 없습니다(unit=" + unit + "): sku=" + sku, e);
        }
    }

    /** Item {@code GET /internal/items/{sku}} 응답 중 우리가 쓰는 필드만 매핑한다(나머지는 ignoreUnknown으로 무시). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ItemResponse(
            String name, String unit, String majorCategory, String middleCategory, int safetyStock) {
    }
}
