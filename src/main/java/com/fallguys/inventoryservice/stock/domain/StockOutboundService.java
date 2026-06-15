package com.fallguys.inventoryservice.stock.domain;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fallguys.inventoryservice.stock.domain.command.OutboundCommand;
import com.fallguys.inventoryservice.stock.domain.command.OutboundLine;
import com.fallguys.inventoryservice.stock.domain.exception.ItemInactiveException;
import com.fallguys.inventoryservice.stock.domain.exception.StockNotFoundException;
import com.fallguys.inventoryservice.stock.domain.query.OutboundMovement;
import com.fallguys.inventoryservice.stock.domain.query.OutboundResult;
import com.fallguys.inventoryservice.warehouse.domain.WarehouseRepository;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseInactiveException;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseNotFoundException;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockOutboundService {

    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    // 같은 inventory 서비스 내 warehouse 애그리거트 참조(코드→id 해석, 존재·활성 검증)
    private final WarehouseRepository warehouseRepository;

    /**
     * SO 출고를 처리한다. 한 창고의 여러 라인을 한 트랜잭션으로 차감하고 라인 수만큼 OUTBOUND 이동 이력을 남긴다.
     * 서비스 간 내부 호출 전용(Tenancy 무관). 입고와 달리 재고 행이 없으면 신규 생성하지 않고 404로 막는다.
     *
     * 흐름:
     * 1) 창고 코드를 실제 창고로 해석한다(없으면 404). 비활성 창고면 출고를 거부한다(400).
     * 2) 멱등: 같은 (sourceRef × 창고)로 이미 적재된 OUTBOUND 이력이 있으면 그 결과를 그대로 반환한다(replay, 재차감 없음).
     * 3) 각 라인의 재고 행을 비관락(SELECT … FOR UPDATE)으로 조회한다. 행이 없으면 404(신규 생성 안 함).
     *    가용재고(현재고)에서 차감하고(초과 시 409) 같은 변동으로 OUTBOUND 이력을 1건 생성·저장한다.
     *
     * 트랜잭션: 쓰기. 라인 원자성 — 한 라인이라도 실패하면 전체 롤백된다(전부 반영 또는 전무).
     *  비관락은 (sku × warehouse) 행 단위로 동시 출고를 직렬화해 가용재고 검증과 차감 사이의 경합(음수 재고)을 막으며,
     *  잠금은 트랜잭션 커밋/롤백까지 유지된다. 잠금 대기 초과는 LOCK_TIMEOUT(409)로 매핑되어 호출 측 재시도를 유도한다. 외부 호출 없음.
     *  서비스 간 분산 일관성(호출 측 실패 시 보상)은 Saga로 2차에 다룬다. DB 멱등 백스톱: UNIQUE(source_ref, source_line_no, warehouse_id).
     *
     * 예외:
     * - 창고 없음: WarehouseNotFoundException (404)
     * - 비활성 창고: WarehouseInactiveException (400)
     * - 재고 행 없음(신규 생성 미지원): StockNotFoundException (404)
     * - 비활성 부품: ItemInactiveException (400)
     * - 가용재고 초과 차감: InsufficientStockException (409)
     * - 잠금 대기 초과: PessimisticLockingFailureException → LOCK_TIMEOUT (409, 핸들러 매핑)
     */
    @Transactional
    public OutboundResult outbound(OutboundCommand command) {
        WarehouseSummaryForEdit warehouse = warehouseRepository.findForEditByCode(command.warehouseCode())
                .orElseThrow(() -> new WarehouseNotFoundException(command.warehouseCode()));
        if (!warehouse.active()) {
            throw new WarehouseInactiveException(command.warehouseCode());
        }
        Long warehouseId = warehouse.id();

        List<OutboundMovement> alreadyProcessed = stockMovementRepository
                .findOutboundBySourceRefAndWarehouseCode(command.sourceRef(), command.warehouseCode());
        if (!alreadyProcessed.isEmpty()) {
            // 응답 유실로 인한 네트워크 재시도에 대해 sales가 정상 response를 받게 한다(재차감 없이 이전 결과 replay).
            return new OutboundResult(command.sourceRef(), command.warehouseCode(), alreadyProcessed);
        }

        List<OutboundMovement> movements = new ArrayList<>();
        for (OutboundLine line : command.lines()) {
            Stock stock = stockRepository.findBySkuAndWarehouseIdForUpdate(line.sku(), warehouseId)
                    .orElseThrow(() -> new StockNotFoundException(command.warehouseCode(), line.sku()));
            // 비활성 부품(SKU)은 출고할 수 없다(아이템 활성 여부는 stock에 반정규화).
            if (!stock.isItemActive()) {
                throw new ItemInactiveException(line.sku());
            }
            int delta = stock.decrease(line.quantity());
            stockRepository.save(stock);
            StockMovement movement = StockMovement.createOutbound(
                    line.sku(), stock.getItemName(), stock.getItemUnit(), warehouseId, delta,
                    command.sourceRef(), line.sourceLineNo(), stock.getQuantity(),
                    command.executorEmpNo(), command.executorName());
            StockMovement saved = stockMovementRepository.save(movement);
            movements.add(new OutboundMovement(
                    saved.getId(), saved.getSku(), saved.getDelta(), saved.getStockAfter()));
        }
        return new OutboundResult(command.sourceRef(), command.warehouseCode(), movements);
    }
}
