package com.fallguys.inventoryservice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.domain.model.WarehouseType;
import com.fallguys.inventoryservice.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.domain.query.WarehouseSummary;

class WarehouseServiceTest {

    @Test
    void 조회조건으로_레포지토리를_호출하고_결과를_그대로_반환한다() {
        WarehouseSummary summary = new WarehouseSummary(
                1L, "HQ-001", "본사 중앙창고", WarehouseType.HQ, null, true,
                Instant.parse("2024-01-15T09:00:00Z"), Instant.parse("2024-01-15T09:00:00Z"));
        StubWarehouseRepository repository = new StubWarehouseRepository(List.of(summary));
        WarehouseService service = new WarehouseService(repository);
        WarehouseSearchQuery query = WarehouseSearchQuery.of("본사", "HQ", "ACTIVE", "code,asc");

        List<WarehouseSummary> result = service.search(query);

        assertThat(result).containsExactly(summary);
        assertThat(repository.received).isSameAs(query);
    }

    @Test
    void 매칭이_없으면_빈_리스트를_반환한다() {
        WarehouseService service = new WarehouseService(new StubWarehouseRepository(List.of()));

        List<WarehouseSummary> result = service.search(WarehouseSearchQuery.of(null, null, null, null));

        assertThat(result).isEmpty();
    }

    private static final class StubWarehouseRepository implements WarehouseRepository {
        private final List<WarehouseSummary> result;
        private WarehouseSearchQuery received;

        private StubWarehouseRepository(List<WarehouseSummary> result) {
            this.result = result;
        }

        @Override
        public List<WarehouseSummary> search(WarehouseSearchQuery query) {
            this.received = query;
            return result;
        }
    }
}
