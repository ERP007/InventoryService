package com.fallguys.inventoryservice.stock.domain;

import java.util.List;
import java.util.Optional;

import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;
import com.fallguys.inventoryservice.stock.domain.query.StockDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockQuantity;
import com.fallguys.inventoryservice.stock.domain.query.StockSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuRow;
import com.fallguys.inventoryservice.stock.domain.query.StockStatusCount;
import com.fallguys.inventoryservice.stock.domain.query.StockSummaryPage;

/**
 * [DIP] 재고 영속성 추상화. 도메인이 정의하고 infrastructure가 구현한다.
 */
public interface StockRepository {

    /** 조회 조건(검색·창고필터·상태·정렬·페이지)에 맞는 재고 목록 페이지를 반환한다. 매칭이 없으면 빈 페이지. */
    StockSummaryPage search(StockSearchQuery query);

    /** (창고 코드 × sku) 단건 재고를 조회한다. 재고 행이 없으면 empty(빈 stock fallback은 서비스가 결정). */
    Optional<StockDetail> findDetailByWarehouseCodeAndSku(String warehouseCode, String sku);

    /** (창고 코드 × SKU 집합)의 현재고·안전재고를 일괄 조회한다(내부 호출). 재고 행이 없는 SKU는 결과에서 생략된다. */
    List<StockQuantity> findQuantitiesByWarehouseCodeAndSkus(String warehouseCode, List<String> skus);

    /** (sku × warehouse) 조합의 재고 존재 여부. 신규 생성 전 중복 검사에 사용한다. */
    boolean existsBySkuAndWarehouseId(String sku, Long warehouseId);

    /** 신규 재고를 저장하고 발급된 식별자를 반환한다. */
    Long save(Stock stock);

    /** 저장 직후 식별자로 생성 결과(창고 코드 조인 포함)를 조회한다. 없으면 empty. */
    Optional<StockCreateResult> findResultById(Long id);

    /** sku의 창고별 재고 행을 조회한다(상세 패널). warehouseCodes가 비어있으면 전체 창고, 있으면 해당 창고로 한정. */
    List<StockSkuRow> findSkuWarehouseStocks(String sku, List<String> warehouseCodes);

    /** 범위 내 (sku × warehouse) 포지션의 총/부족/무재고 수를 센다(KPI). warehouseCodes가 비어있으면 전사. */
    StockStatusCount countByStatus(List<String> warehouseCodes);

    /** 조정 대상 재고를 (sku × warehouseCode)로 조회한다(수정용 — 같은 트랜잭션에서 save 시 @Version 적용). 없으면 empty. */
    Optional<Stock> findBySkuAndWarehouseCode(String sku, String warehouseCode);
}
