package com.fallguys.inventoryservice.warehouse.domain;

import com.fallguys.inventoryservice.branchlocation.domain.BranchLocationRepository;

import com.fallguys.inventoryservice.branchlocation.domain.BranchLocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.warehouse.domain.command.ChangeWarehouseActiveCommand;
import com.fallguys.inventoryservice.warehouse.domain.command.CreateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.command.UpdateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.exception.BranchAlreadyAssignedException;
import com.fallguys.inventoryservice.warehouse.domain.exception.BranchNotFoundException;
import com.fallguys.inventoryservice.warehouse.domain.exception.LastActiveHqWarehouseException;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseBranchRuleException;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseCodeDuplicateException;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseNotFoundException;
import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseHqSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;

class WarehouseServiceTest {

    private static WarehouseSummary summary(Long id, String code) {
        return new WarehouseSummary(id, code, "서울 2창고", WarehouseType.DEALER, "서울 강남지점", "서울 강남구", true,
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

    // ---- findHqWarehouses ----

    @Test
    void findHqWarehouses는_레포지토리의_활성HQ_목록을_그대로_반환한다() {
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of());
        repository.hqSummaries = List.of(
                new WarehouseHqSummary(1L, "WH-HQ-001", "본사 서울 창고"),
                new WarehouseHqSummary(5L, "WH-HQ-002", "본사 부산 창고"));
        WarehouseService service = new WarehouseService(repository, new StubBranchLocationRepository(true));

        List<WarehouseHqSummary> result = service.findHqWarehouses();

        assertThat(result).extracting(WarehouseHqSummary::code)
                .containsExactly("WH-HQ-001", "WH-HQ-002");
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

    @Test
    void DEALER인데_소속지점이_이미_다른창고에_할당됐으면_BranchAlreadyAssignedException을_던지고_저장하지_않는다() {
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of());
        repository.branchAlreadyAssigned = true;
        WarehouseService service = new WarehouseService(repository, new StubBranchLocationRepository(true));

        assertThatThrownBy(() -> service.create(
                new CreateWarehouseCommand("BR-SE-002", "서울 2창고", WarehouseType.DEALER, 3L, null)))
                .isInstanceOf(BranchAlreadyAssignedException.class);
        assertThat(repository.savedWarehouse).isNull();
    }

    // ---- getByCode ----

    @Test
    void getByCode는_조회된_상세_읽기모델을_반환한다() {
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of());
        repository.summaryForEdit = new WarehouseSummaryForEdit(
                2L, "WH-SE-001", "서울 1창고", WarehouseType.DEALER, 3L, "서울 강남지점", "서울 강남구",
                true, Instant.parse("2024-03-10T09:00:00Z"), Instant.parse("2025-11-02T14:30:00Z"), 5L);
        WarehouseService service = new WarehouseService(repository, new StubBranchLocationRepository(true));

        WarehouseSummaryForEdit result = service.getByCode("WH-SE-001");

        assertThat(result.code()).isEqualTo("WH-SE-001");
        assertThat(result.branchId()).isEqualTo(3L);
        assertThat(result.version()).isEqualTo(5L);
        assertThat(repository.findForEditCodeArg).isEqualTo("WH-SE-001");
    }

    @Test
    void getByCode는_없으면_WarehouseNotFoundException을_던진다() {
        WarehouseService service = new WarehouseService(
                new StubWarehouseRepository(List.of()), new StubBranchLocationRepository(true));

        assertThatThrownBy(() -> service.getByCode("NOPE"))
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

        WarehouseSummaryForEdit result = service.update("WH-SE-001",
                new UpdateWarehouseCommand("서울 1창고 (강남)", WarehouseType.DEALER, 3L, "새 주소", 5L));

        assertThat(result.version()).isEqualTo(6L);
        assertThat(repository.updateCodeArg).isEqualTo("WH-SE-001");
        assertThat(repository.updateCommandArg.name()).isEqualTo("서울 1창고 (강남)");
    }

    @Test
    void update는_유형_정합_위반이면_WarehouseBranchRuleException을_던지고_위임하지_않는다() {
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of());
        WarehouseService service = new WarehouseService(repository, new StubBranchLocationRepository(true));

        assertThatThrownBy(() -> service.update("WH-SE-001",
                new UpdateWarehouseCommand("본사", WarehouseType.HQ, 3L, null, 5L)))
                .isInstanceOf(WarehouseBranchRuleException.class);
        assertThat(repository.updateCodeArg).isNull();
    }

    @Test
    void update는_소속지점이_없으면_BranchNotFoundException을_던지고_위임하지_않는다() {
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of());
        WarehouseService service = new WarehouseService(repository, new StubBranchLocationRepository(false));

        assertThatThrownBy(() -> service.update("WH-SE-001",
                new UpdateWarehouseCommand("서울 1창고", WarehouseType.DEALER, 99L, null, 5L)))
                .isInstanceOf(BranchNotFoundException.class);
        assertThat(repository.updateCodeArg).isNull();
    }

    @Test
    void update는_소속지점이_이미_다른창고에_할당됐으면_BranchAlreadyAssignedException을_던지고_위임하지_않는다() {
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of());
        repository.branchAlreadyAssigned = true;
        WarehouseService service = new WarehouseService(repository, new StubBranchLocationRepository(true));

        assertThatThrownBy(() -> service.update("BR-SE-001",
                new UpdateWarehouseCommand("서울 1창고", WarehouseType.DEALER, 3L, null, 5L)))
                .isInstanceOf(BranchAlreadyAssignedException.class);
        assertThat(repository.updateCodeArg).isNull();
    }

    // ---- changeActive ----

    private static WarehouseSummaryForEdit forEdit(boolean active, long version) {
        return new WarehouseSummaryForEdit(
                2L, "WH-SE-001", "서울 1창고", WarehouseType.DEALER, 3L, "서울 강남지점", "주소",
                active, Instant.parse("2024-03-10T09:00:00Z"), Instant.parse("2026-05-28T14:32:00Z"), version);
    }

    private static WarehouseSummaryForEdit forEditHq(boolean active, long version) {
        return new WarehouseSummaryForEdit(
                1L, "WH-HQ-001", "본사 중앙창고", WarehouseType.HQ, null, null, "주소",
                active, Instant.parse("2024-03-10T09:00:00Z"), Instant.parse("2026-05-28T14:32:00Z"), version);
    }

    @Test
    void changeActive는_같은_값이면_no_op으로_현재상태를_반환하고_위임하지_않는다() {
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of());
        repository.summaryForEdit = forEdit(true, 6L);
        WarehouseService service = new WarehouseService(repository, new StubBranchLocationRepository(true));

        WarehouseSummaryForEdit result = service.changeActive("WH-SE-001", new ChangeWarehouseActiveCommand(true, 6L));

        assertThat(result.active()).isTrue();
        assertThat(result.version()).isEqualTo(6L);
        assertThat(repository.changeActiveCalled).isFalse();
    }

    @Test
    void changeActive는_값이_다르면_영속성에_위임한다() {
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of());
        repository.summaryForEdit = forEdit(true, 6L);
        repository.changeActiveResult = forEdit(false, 7L);
        WarehouseService service = new WarehouseService(repository, new StubBranchLocationRepository(true));

        WarehouseSummaryForEdit result = service.changeActive("WH-SE-001", new ChangeWarehouseActiveCommand(false, 6L));

        assertThat(result.active()).isFalse();
        assertThat(result.version()).isEqualTo(7L);
        assertThat(repository.changeActiveCalled).isTrue();
    }

    @Test
    void changeActive는_없으면_WarehouseNotFoundException을_던진다() {
        WarehouseService service = new WarehouseService(
                new StubWarehouseRepository(List.of()), new StubBranchLocationRepository(true));

        assertThatThrownBy(() -> service.changeActive("NOPE", new ChangeWarehouseActiveCommand(false, 6L)))
                .isInstanceOf(WarehouseNotFoundException.class);
    }

    @Test
    void changeActive_마지막_활성_HQ를_비활성화하면_LastActiveHqWarehouseException을_던지고_위임하지_않는다() {
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of());
        repository.summaryForEdit = forEditHq(true, 6L);
        repository.hqSummaries = List.of(new WarehouseHqSummary(1L, "WH-HQ-001", "본사 중앙창고")); // 활성 HQ 1개뿐
        WarehouseService service = new WarehouseService(repository, new StubBranchLocationRepository(true));

        assertThatThrownBy(() -> service.changeActive("WH-HQ-001", new ChangeWarehouseActiveCommand(false, 6L)))
                .isInstanceOf(LastActiveHqWarehouseException.class);
        assertThat(repository.changeActiveCalled).isFalse();
    }

    @Test
    void changeActive_다른_활성_HQ가_있으면_HQ_비활성화를_위임한다() {
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of());
        repository.summaryForEdit = forEditHq(true, 6L);
        repository.hqSummaries = List.of(
                new WarehouseHqSummary(1L, "WH-HQ-001", "본사 중앙창고"),
                new WarehouseHqSummary(5L, "WH-HQ-002", "본사 제2창고")); // 활성 HQ 2개
        repository.changeActiveResult = forEditHq(false, 7L);
        WarehouseService service = new WarehouseService(repository, new StubBranchLocationRepository(true));

        WarehouseSummaryForEdit result = service.changeActive("WH-HQ-001", new ChangeWarehouseActiveCommand(false, 6L));

        assertThat(result.active()).isFalse();
        assertThat(repository.changeActiveCalled).isTrue();
    }

    @Test
    void changeActive_비활성_HQ_재활성화는_활성HQ_규칙과_무관하게_위임한다() {
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of());
        repository.summaryForEdit = forEditHq(false, 6L); // 현재 비활성 HQ
        repository.changeActiveResult = forEditHq(true, 7L);
        WarehouseService service = new WarehouseService(repository, new StubBranchLocationRepository(true));

        WarehouseSummaryForEdit result = service.changeActive("WH-HQ-001", new ChangeWarehouseActiveCommand(true, 6L));

        assertThat(result.active()).isTrue();
        assertThat(repository.changeActiveCalled).isTrue();
    }

    private static final class StubWarehouseRepository implements WarehouseRepository {
        private final List<WarehouseSummary> searchResult;
        private List<WarehouseHqSummary> hqSummaries = List.of();
        private WarehouseSearchQuery received;
        private boolean codeExists = false;
        private boolean branchAlreadyAssigned = false;
        private Warehouse savedWarehouse;
        private WarehouseSummary summaryAfterSave;
        private WarehouseSummaryForEdit summaryForEdit;
        private String findForEditCodeArg;
        private WarehouseSummaryForEdit updatedResult;
        private String updateCodeArg;
        private UpdateWarehouseCommand updateCommandArg;
        private WarehouseSummaryForEdit changeActiveResult;
        private boolean changeActiveCalled = false;

        private StubWarehouseRepository(List<WarehouseSummary> searchResult) {
            this.searchResult = searchResult;
        }

        @Override
        public List<WarehouseSummary> search(WarehouseSearchQuery query) {
            this.received = query;
            return searchResult;
        }

        @Override
        public List<WarehouseHqSummary> findActiveHq() {
            return hqSummaries;
        }

        @Override
        public boolean existsByCode(String code) {
            return codeExists;
        }

        @Override
        public boolean existsByBranchIdExcludingCode(Long branchId, String excludeCode) {
            return branchAlreadyAssigned;
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
        public Optional<WarehouseSummaryForEdit> findForEditByCode(String code) {
            this.findForEditCodeArg = code;
            return Optional.ofNullable(summaryForEdit);
        }

        @Override
        public WarehouseSummaryForEdit update(String code, UpdateWarehouseCommand command) {
            this.updateCodeArg = code;
            this.updateCommandArg = command;
            return updatedResult;
        }

        @Override
        public WarehouseSummaryForEdit changeActive(String code, ChangeWarehouseActiveCommand command) {
            this.changeActiveCalled = true;
            return changeActiveResult;
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
