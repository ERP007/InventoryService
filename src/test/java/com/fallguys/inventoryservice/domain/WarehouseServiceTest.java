package com.fallguys.inventoryservice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.domain.command.CreateWarehouseCommand;
import com.fallguys.inventoryservice.domain.exception.BranchNotFoundException;
import com.fallguys.inventoryservice.domain.exception.WarehouseCodeDuplicateException;
import com.fallguys.inventoryservice.domain.model.WarehouseType;
import com.fallguys.inventoryservice.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.domain.query.WarehouseSummary;

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

    private static final class StubWarehouseRepository implements WarehouseRepository {
        private final List<WarehouseSummary> searchResult;
        private WarehouseSearchQuery received;
        private boolean codeExists = false;
        private Warehouse savedWarehouse;
        private WarehouseSummary summaryAfterSave;

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
