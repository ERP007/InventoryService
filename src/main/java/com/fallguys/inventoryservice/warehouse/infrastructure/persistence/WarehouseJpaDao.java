package com.fallguys.inventoryservice.warehouse.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseHqSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseOption;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummary;

public interface WarehouseJpaDao extends JpaRepository<WarehouseEntity, Long> {

    /**
     * 조건부 필터로 창고를 검색해 읽기 모델(WarehouseSummary)로 투영한다.
     *
     * 조인: BranchLocation을 LEFT JOIN하여 소속 지점명을 가져온다(HQ 유형은 branchId가 null이라 지점명 null).
     * 필터: 각 파라미터가 null이면 해당 조건을 건너뛴다(동적 조회).
     *   - keyword: 창고명/코드 부분 일치(대소문자 무시). 와일드카드(%)와 소문자화는 호출부에서 적용한
     *              소문자 LIKE 패턴으로 들어온다. (PostgreSQL의 untyped-null → bytea 추론을 피하려
     *              CONCAT/LOWER(param) 대신 타입이 명확한 String 파라미터로 비교한다.)
     *   - type:    유형 일치
     *   - active:  활성 상태 일치(ALL 조회 시 null로 들어와 조건 제외)
     * 정렬: Sort 파라미터로 위임(허용 속성은 호출부에서 화이트리스트 검증됨).
     */
    @Query("""
            SELECT new com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummary(
                w.id, w.code, w.name, w.type, b.name, w.address, w.active, w.createdAt, w.updatedAt)
            FROM WarehouseEntity w
            LEFT JOIN BranchLocationEntity b ON b.id = w.branchId
            WHERE (:keyword IS NULL
                   OR LOWER(w.code) LIKE :keyword
                   OR LOWER(w.name) LIKE :keyword)
              AND (:type IS NULL OR w.type = :type)
              AND (:active IS NULL OR w.active = :active)
            """)
    List<WarehouseSummary> search(
            @Param("keyword") String keyword,
            @Param("type") WarehouseType type,
            @Param("active") Boolean active,
            Sort sort
    );

    /** 창고 코드 존재 여부(등록 전 중복 검사). */
    boolean existsByCode(String code);

    /** 소속 지점에 할당된 창고 존재 여부(지점↔창고 1:1 매핑 검증). */
    boolean existsByBranchId(Long branchId);

    /** 지정 창고(code)를 제외하고 소속 지점에 할당된 창고 존재 여부(1:1 매핑 검증, 수정 시 자기 제외). */
    boolean existsByBranchIdAndCodeNot(Long branchId, String code);

    /**
     * 식별자로 창고를 읽기 모델(WarehouseSummary)로 투영한다(등록 직후 응답 구성용).
     *
     * 조인: BranchLocation을 LEFT JOIN하여 소속 지점명을 가져온다(HQ는 branchId가 null이라 지점명 null).
     */
    @Query("""
            SELECT new com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummary(
                w.id, w.code, w.name, w.type, b.name, w.address, w.active, w.createdAt, w.updatedAt)
            FROM WarehouseEntity w
            LEFT JOIN BranchLocationEntity b ON b.id = w.branchId
            WHERE w.id = :id
            """)
    Optional<WarehouseSummary> findSummaryById(@Param("id") Long id);


    @Query("""
        SELECT new com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit(
            w.id, w.code, w.name, w.type, w.branchId, b.name, w.address,
            w.active, w.createdAt, w.updatedAt, w.version)
        FROM WarehouseEntity w
        LEFT JOIN BranchLocationEntity b ON b.id = w.branchId
        WHERE w.code = :code
        """)
    Optional<WarehouseSummaryForEdit> findForEditByCode(@Param("code") String code);

    /** 수정·상태전환 시 변경 대상 영속 엔티티를 창고 코드로 로드한다(code는 시스템 유일). */
    Optional<WarehouseEntity> findByCode(String code);

    /**
     * 출고 창고 선택 드롭다운용으로 활성 본사 창고를 슬림 모델(id·code·name)로 투영한다.
     *
     * 필터: type=HQ(본사) AND active=true. 조인 없음(소속 지점이 없는 HQ 전용이라 branch 불필요).
     * 정렬: code 오름차순(드롭다운 표시 순서 고정).
     */
    @Query("""
        SELECT new com.fallguys.inventoryservice.warehouse.domain.query.WarehouseHqSummary(
            w.id, w.code, w.name)
        FROM WarehouseEntity w
        WHERE w.type = com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType.HQ
          AND w.active = true
        ORDER BY w.code ASC
        """)
    List<WarehouseHqSummary> findActiveHq();

    /**
     * 창고 선택 드롭다운용으로 활성 창고를 슬림 모델(code·표시명)로 투영한다.
     *
     * 조인: BranchLocation을 LEFT JOIN하여 소속 지점명을 가져온다. 소속 지점이 없는 HQ(branchId null)는
     *      COALESCE로 창고명(w.name)으로 대체한다 → DEALER는 소속 지점명, HQ는 창고명.
     * 필터: active=true(활성 창고만). 정렬: code 오름차순(드롭다운 표시 순서 고정).
     */
    @Query("""
        SELECT new com.fallguys.inventoryservice.warehouse.domain.query.WarehouseOption(
            w.code, COALESCE(b.name, w.name))
        FROM WarehouseEntity w
        LEFT JOIN BranchLocationEntity b ON b.id = w.branchId
        WHERE w.active = true
        ORDER BY w.code ASC
        """)
    List<WarehouseOption> findActiveOptions();
}
