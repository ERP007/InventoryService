package com.fallguys.inventoryservice.warehouse.domain;

import com.fallguys.inventoryservice.branchlocation.domain.BranchLocationRepository;

import com.fallguys.inventoryservice.branchlocation.domain.BranchLocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.warehouse.domain.command.CreateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.command.UpdateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.exception.BranchNotFoundException;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseBranchRuleException;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseCodeDuplicateException;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseNotFoundException;
import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;

class WarehouseServiceTest {

    private static WarehouseSummary summary(Long id, String code) {
        return new WarehouseSummary(id, code, "서울 2창고", WarehouseType.DEALER, "서울 강남지점", true,
                Instant.parse("2026-05-28T14:30:00Z"), Instant.parse("2026-05-28T14:30:00Z"));
    }

    // ---- search ----

    @Test
    void 조회조건으로_레포지토리를_호출하고_결과를_그대로_반환한다() {
        WarehouseSummary s = summary(1L, "HQ-001");
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of(s));
        WarehouseService service = new WarehouseService(repository, new StubBranchLocationRepository(true));
        WarehouseSearchQuery query = WarehouseSearchQuery.of("본사", "HQ", "ACTIVE", "code,asc");

        List<WarehouseSummary> result = service.search(query);

        assertThat(result).containsExactly(s);
        assertThat(repository.received).isSameAs(query);
    }

    @Test
    void 매칭이_없으면_빈_리스트를_반환한다() {
        WarehouseService service = new WarehouseService(
                new StubWarehouseRepository(List.of()), new StubBranchLocationRepository(true));

        List<WarehouseSummary> result = service.search(WarehouseSearchQuery.of(null, null, null, null));

        assertThat(result).isEmpty();
    }

    // ---- create ----

    @Test
    void 정상등록은_저장후_재조회한_읽기모델을_반환하고_active를_true로_저장한다() {
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of());
        repository.summaryAfterSave = summary(24L, "WH-SE-002");
        WarehouseService service = new WarehouseService(repository, new StubBranchLocationRepository(true));

        WarehouseSummary result = service.create(
                new CreateWarehouseCommand("WH-SE-002", "서울 2창고", WarehouseType.DEALER, 3L, "서울 강남구"));

        assertThat(result.id()).isEqualTo(24L);
        assertThat(result.code()).isEqualTo("WH-SE-002");
        assertThat(repository.savedWarehouse).isNotNull();
        assertThat(repository.savedWarehouse.isActive()).isTrue();
    }

    @Test
    void HQ는_소속지점_존재검사_없이_등록된다() {
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of());
        repository.summaryAfterSave = summary(24L, "HQ-002");
        StubBranchLocationRepository branch = new StubBranchLocationRepository(false);
        WarehouseService service = new WarehouseService(repository, branch);

        WarehouseSummary result = service.create(
                new CreateWarehouseCommand("HQ-002", "본사 제2창고", WarehouseType.HQ, null, null));

        assertThat(result.id()).isEqualTo(24L);
        assertThat(branch.existsByIdCalled).isFalse();
    }

    @Test
    void DEALER인데_소속지점이_없으면_BranchNotFoundException을_던지고_저장하지_않는다() {
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of());
        WarehouseService service = new WarehouseService(repository, new StubBranchLocationRepository(false));

        assertThatThrownBy(() -> service.create(
                new CreateWarehouseCommand("WH-SE-002", "서울 2창고", WarehouseType.DEALER, 99L, null)))
                .isInstanceOf(BranchNotFoundException.class);
        assertThat(repository.savedWarehouse).isNull();
    }

    @Test
    void 코드가_중복이면_WarehouseCodeDuplicateException을_던지고_저장하지_않는다() {
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of());
        repository.codeExists = true;
        WarehouseService service = new WarehouseService(repository, new StubBranchLocationRepository(true));

        assertThatThrownBy(() -> service.create(
                new CreateWarehouseCommand("WH-SE-002", "서울 2창고", WarehouseType.DEALER, 3L, null)))
                .isInstanceOf(WarehouseCodeDuplicateException.class);
        assertThat(repository.savedWarehouse).isNull();
    }

    // ---- getById ----

    @Test
    void getById는_조회된_상세_읽기모델을_반환한다() {
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of());
        repository.summaryForEdit = new WarehouseSummaryForEdit(
                2L, "WH-SE-001", "서울 1창고", WarehouseType.DEALER, 3L, "서울 강남지점", "서울 강남구",
                true, Instant.parse("2024-03-10T09:00:00Z"), Instant.parse("2025-11-02T14:30:00Z"), 5L);
        WarehouseService service = new WarehouseService(repository, new StubBranchLocationRepository(true));

        WarehouseSummaryForEdit result = service.getById(2L);

        assertThat(result.id()).isEqualTo(2L);
        assertThat(result.branchId()).isEqualTo(3L);
        assertThat(result.version()).isEqualTo(5L);
    }

    @Test
    void getById는_없으면_WarehouseNotFoundException을_던진다() {
        WarehouseService service = new WarehouseService(
                new StubWarehouseRepository(List.of()), new StubBranchLocationRepository(true));

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(WarehouseNotFoundException.class);
    }

    // ---- update ----

    @Test
    void update는_정합_검증_후_영속성에_위임하고_결과를_반환한다() {
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of());
        repository.updatedResult = new WarehouseSummaryForEdit(
                2L, "WH-SE-001", "서울 1창고 (강남)", WarehouseType.DEALER, 3L, "서울 강남지점", "새 주소",
                true, Instant.parse("2024-03-10T09:00:00Z"), Instant.parse("2026-05-28T14:31:00Z"), 6L);
        WarehouseService service = new WarehouseService(repository, new StubBranchLocationRepository(true));

        WarehouseSummaryForEdit result = service.update(2L,
                new UpdateWarehouseCommand("서울 1창고 (강남)", WarehouseType.DEALER, 3L, "새 주소", 5L));

        assertThat(result.version()).isEqualTo(6L);
        assertThat(repository.updateIdArg).isEqualTo(2L);
        assertThat(repository.updateCommandArg.name()).isEqualTo("서울 1창고 (강남)");
    }

    @Test
    void update는_유형_정합_위반이면_WarehouseBranchRuleException을_던지고_위임하지_않는다() {
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of());
        WarehouseService service = new WarehouseService(repository, new StubBranchLocationRepository(true));

        assertThatThrownBy(() -> service.update(2L,
                new UpdateWarehouseCommand("본사", WarehouseType.HQ, 3L, null, 5L)))
                .isInstanceOf(WarehouseBranchRuleException.class);
        assertThat(repository.updateIdArg).isNull();
    }

    @Test
    void update는_소속지점이_없으면_BranchNotFoundException을_던지고_위임하지_않는다() {
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of());
        WarehouseService service = new WarehouseService(repository, new StubBranchLocationRepository(false));

        assertThatThrownBy(() -> service.update(2L,
                new UpdateWarehouseCommand("서울 1창고", WarehouseType.DEALER, 99L, null, 5L)))
                .isInstanceOf(BranchNotFoundException.class);
        assertThat(repository.updateIdArg).isNull();
    }

    private static final class StubWarehouseRepository implements WarehouseRepository {
        private final List<WarehouseSummary> searchResult;
        private WarehouseSearchQuery received;
        private boolean codeExists = false;
        private Warehouse savedWarehouse;
        private WarehouseSummary summaryAfterSave;
        private WarehouseSummaryForEdit summaryForEdit;
        private WarehouseSummaryForEdit updatedResult;
        private Long updateIdArg;
        private UpdateWarehouseCommand updateCommandArg;

        private StubWarehouseRepository(List<WarehouseSummary> searchResult) {
            this.searchResult = searchResult;
        }

        @Override
        public List<WarehouseSummary> search(WarehouseSearchQuery query) {
            this.received = query;
            return searchResult;
        }

        @Override
        public boolean existsByCode(String code) {
            return codeExists;
        }

        @Override
        public Long save(Warehouse warehouse) {
            this.savedWarehouse = warehouse;
            return 24L;
        }

        @Override
        public Optional<WarehouseSummary> findSummaryById(Long id) {
            return Optional.ofNullable(summaryAfterSave);
        }

        @Override
        public Optional<WarehouseSummaryForEdit> findForEditById(Long id) {
            return Optional.ofNullable(summaryForEdit);
        }

        @Override
        public WarehouseSummaryForEdit update(Long id, UpdateWarehouseCommand command) {
            this.updateIdArg = id;
            this.updateCommandArg = command;
            return updatedResult;
        }
    }

    private static final class StubBranchLocationRepository implements BranchLocationRepository {
        private final boolean branchExists;
        private boolean existsByIdCalled = false;

        private StubBranchLocationRepository(boolean branchExists) {
            this.branchExists = branchExists;
        }

        @Override
        public boolean existsByName(String name) {
            return false;
        }

        @Override
        public boolean existsById(Long id) {
            existsByIdCalled = true;
            return branchExists;
        }

        @Override
        public BranchLocation save(BranchLocation branchLocation) {
            return branchLocation;
        }

        @Override
        public List<BranchLocation> findAll() {
            return List.of();
        }
    }
}
