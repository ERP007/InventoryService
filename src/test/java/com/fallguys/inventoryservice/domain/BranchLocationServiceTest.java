package com.fallguys.inventoryservice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.domain.command.CreateBranchLocationCommand;
import com.fallguys.inventoryservice.domain.exception.BranchLocationNameDuplicateException;

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

    private static final class StubBranchLocationRepository implements BranchLocationRepository {
        private final boolean exists;
        private final long assignedId;
        private String existsByNameArg;
        private BranchLocation savedArg;

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
        public BranchLocation save(BranchLocation branchLocation) {
            this.savedArg = branchLocation;
            return BranchLocation.of(assignedId, branchLocation.getName());
        }
    }
}
