package com.fallguys.inventoryservice.branchlocation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.branchlocation.domain.command.CreateBranchLocationCommand;
import com.fallguys.inventoryservice.branchlocation.domain.exception.BranchLocationNameDuplicateException;

class BranchLocationServiceTest {

    @Test
    void 중복이_아니면_신규지점을_저장하고_식별자가_발급된_도메인을_반환한다() {
        StubBranchLocationRepository repository = new StubBranchLocationRepository(false, 9L);
        BranchLocationService service = new BranchLocationService(repository);

        BranchLocation created = service.create(new CreateBranchLocationCommand("수원 영통지점"));

        assertThat(created.getId()).isEqualTo(9L);
        assertThat(created.getName()).isEqualTo("수원 영통지점");
        assertThat(repository.existsByNameArg).isEqualTo("수원 영통지점");
        assertThat(repository.savedArg).isNotNull();
        assertThat(repository.savedArg.getId()).isNull();
        assertThat(repository.savedArg.getName()).isEqualTo("수원 영통지점");
    }

    @Test
    void 지점명이_중복이면_예외를_던지고_저장하지_않는다() {
        StubBranchLocationRepository repository = new StubBranchLocationRepository(true, 9L);
        BranchLocationService service = new BranchLocationService(repository);

        assertThatThrownBy(() -> service.create(new CreateBranchLocationCommand("중복지점")))
                .isInstanceOf(BranchLocationNameDuplicateException.class)
                .hasMessageContaining("중복지점");
        assertThat(repository.savedArg).isNull();
    }

    @Test
    void findAll은_레포지토리의_전체_지점목록을_그대로_반환한다() {
        StubBranchLocationRepository repository = new StubBranchLocationRepository(false, 0L);
        repository.findAllResult = List.of(
                BranchLocation.of(1L, "서울 강남지점"),
                BranchLocation.of(2L, "서울 송파지점"));
        BranchLocationService service = new BranchLocationService(repository);

        List<BranchLocation> result = service.findAll();

        assertThat(result).extracting(BranchLocation::getName)
                .containsExactly("서울 강남지점", "서울 송파지점");
    }

    @Test
    void findAll은_지점이_없으면_빈_목록을_반환한다() {
        BranchLocationService service = new BranchLocationService(new StubBranchLocationRepository(false, 0L));

        assertThat(service.findAll()).isEmpty();
    }

    @Test
    void findUnassigned는_레포지토리의_미할당_지점목록을_그대로_반환한다() {
        StubBranchLocationRepository repository = new StubBranchLocationRepository(false, 0L);
        repository.unassignedResult = List.of(
                BranchLocation.of(6L, "서울 금천지점1"),
                BranchLocation.of(7L, "수원 영통지점"));
        BranchLocationService service = new BranchLocationService(repository);

        List<BranchLocation> result = service.findUnassigned();

        assertThat(result).extracting(BranchLocation::getName)
                .containsExactly("서울 금천지점1", "수원 영통지점");
    }

    private static final class StubBranchLocationRepository implements BranchLocationRepository {
        private final boolean exists;
        private final long assignedId;
        private String existsByNameArg;
        private BranchLocation savedArg;
        private List<BranchLocation> findAllResult = List.of();
        private List<BranchLocation> unassignedResult = List.of();

        private StubBranchLocationRepository(boolean exists, long assignedId) {
            this.exists = exists;
            this.assignedId = assignedId;
        }

        @Override
        public boolean existsByName(String name) {
            this.existsByNameArg = name;
            return exists;
        }

        @Override
        public boolean existsById(Long id) {
            return false;
        }

        @Override
        public BranchLocation save(BranchLocation branchLocation) {
            this.savedArg = branchLocation;
            return BranchLocation.of(assignedId, branchLocation.getName());
        }

        @Override
        public List<BranchLocation> findAll() {
            return findAllResult;
        }

        @Override
        public List<BranchLocation> findUnassigned() {
            return unassignedResult;
        }
    }
}
