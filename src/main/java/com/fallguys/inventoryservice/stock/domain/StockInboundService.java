package com.fallguys.inventoryservice.stock.domain;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fallguys.inventoryservice.stock.domain.command.InboundCommand;
import com.fallguys.inventoryservice.stock.domain.command.InboundLine;
import com.fallguys.inventoryservice.stock.domain.exception.ItemInactiveException;
import com.fallguys.inventoryservice.stock.domain.exception.ItemNotFoundException;
import com.fallguys.inventoryservice.stock.domain.query.InboundMovement;
import com.fallguys.inventoryservice.stock.domain.query.InboundResult;
import com.fallguys.inventoryservice.warehouse.domain.WarehouseRepository;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseInactiveException;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseNotFoundException;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockInboundService {

    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    // 같은 inventory 서비스 내 warehouse 애그리거트 참조(코드→id 해석, 존재·활성 검증)
    private final WarehouseRepository warehouseRepository;
    // [DIP] 신규 재고행 생성 시 부품명·단위·기본 안전재고를 Item 서비스에서 조회
    private final ItemInfoProvider itemInfoProvider;

    /**
     * PO 입고·SO 도착을 처리한다. 한 창고의 여러 라인을 한 트랜잭션으로 증가시키고 라인 수만큼 INBOUND 이동 이력을 남긴다.
     * 서비스 간 내부 호출 전용(Tenancy 무관).
     *
     * 흐름:
     * 1) 창고 코드를 실제 창고로 해석한다(없으면 404). 비활성 창고면 입고를 거부한다(400).
     * 2) 멱등: 같은 (sourceRef × 창고)로 이미 적재된 INBOUND 이력이 있으면 그 결과를 그대로 반환한다(replay, 재증가 없음).
     * 3) 각 라인의 재고 행을 비관락(SELECT … FOR UPDATE)으로 조회해 수량을 증가시킨다. 행이 없으면(첫 입고) Item 서비스에서
     *    부품명·단위·기본 안전재고를 받아 신규 행을 만든다(Item에 sku가 없으면 404, Item 호출 실패면 502).
     *    같은 변동으로 INBOUND 이력을 1건 생성·저장한다.
     *
     * 트랜잭션: 쓰기. 라인 원자성 — 한 라인이라도 실패하면 전체 롤백된다(전부 반영 또는 전무).
     *  비관락은 (sku × warehouse) 기존 행 단위로 동시 입고를 직렬화한다(증가 경합 시 낙관락 충돌 대신 짧은 대기). 잠금은 커밋/롤백까지 유지되고,
     *  잠금 대기 초과는 LOCK_TIMEOUT(409)으로 매핑되어 호출 측 재시도를 유도한다. 신규 행 첫 입고는 잠글 행이 없어,
     *  동시 INSERT는 UNIQUE(sku, warehouse_id) 위반으로 backstop되고 호출 측 재시도로 흡수된다.
     *  신규 행 생성 시 Item 외부 호출이 트랜잭션 안에서 일어난다(첫 입고에 한정, 빈도 낮아 허용. 통합 비활성이면 호출 없이 404).
     *  서비스 간 분산 일관성(호출 측 실패 시 보상)은 Saga로 2차에 다룬다. DB 멱등 백스톱: UNIQUE(source_ref, source_line_no, warehouse_id).
     *
     * 예외:
     * - 창고 없음: WarehouseNotFoundException (404)
     * - 비활성 창고: WarehouseInactiveException (400)
     * - 비활성 부품(기존 재고 행): ItemInactiveException (400)
     * - 신규 행인데 Item에 부품 없음(통합 비활성 포함): ItemNotFoundException (404)
     * - Item 호출 기술 실패: ItemServiceUnavailableException (502)
     * - 잠금 대기 초과: PessimisticLockingFailureException → LOCK_TIMEOUT (409, 핸들러 매핑)
     */
    @Transactional
    public InboundResult inbound(InboundCommand command) {
        WarehouseSummaryForEdit warehouse = warehouseRepository.findForEditByCode(command.warehouseCode())
                .orElseThrow(() -> new WarehouseNotFoundException(command.warehouseCode()));
        if (!warehouse.active()) { // 창고 없으면 throw
            throw new WarehouseInactiveException(command.warehouseCode());
        }
        Long warehouseId = warehouse.id();

        List<InboundMovement> alreadyProcessed = stockMovementRepository
                .findInboundBySourceRefAndWarehouseCode(command.sourceRef(), command.warehouseCode());
        if (!alreadyProcessed.isEmpty()) { // sourceRef 가 db에 없어야 정상 호출
            return new InboundResult(command.sourceRef(), command.warehouseCode(), alreadyProcessed);
        }

        // sourceRef 가 db에 있으면 sourceRef 로 진행된 결과를 반환한다
        // 응답 유실로 인한 네트워크 재시도에 대해 procurement/sales가 정상 response를 받게 하기 위함이다
        List<InboundMovement> movements = new ArrayList<>();
        for (InboundLine line : command.lines()) {
            Stock stock = stockRepository.findBySkuAndWarehouseIdForUpdate(line.sku(), warehouseId)
                    .orElseGet(() -> createNewStock(line.sku(), warehouseId));
            // 비활성 부품(SKU)은 입고할 수 없다. 신규 행은 활성으로 생성되므로 이 검증을 통과한다.
            if (!stock.isItemActive()) {
                throw new ItemInactiveException(line.sku());
            }
            int delta = stock.increase(line.quantity());
            stockRepository.save(stock);
            StockMovement movement = StockMovement.createInbound(
                    line.sku(), stock.getItemName(), stock.getItemUnit(), warehouseId, delta,
                    command.sourceRef(), line.sourceLineNo(), stock.getQuantity(),
                    command.executorEmpNo(), command.executorName());
            StockMovement saved = stockMovementRepository.save(movement);
            movements.add(new InboundMovement(
                    saved.getId(), saved.getSku(), saved.getDelta(), saved.getStockAfter()));
        }
        return new InboundResult(command.sourceRef(), command.warehouseCode(), movements);
    }

    /**
     * 신규 (sku×창고) 재고행을 만든다(첫 입고). Item 서비스에서 부품명·단위·기본 안전재고를 받아 수량 0으로 생성한다(증가는 호출부가 수행).
     *
     * @throws ItemNotFoundException Item 마스터에 sku가 없거나 통합이 비활성이라 정보를 얻지 못할 때(404)
     */
    private Stock createNewStock(String sku, Long warehouseId) {
        return itemInfoProvider.findBySku(sku) // yaml 에 ITEM_INTEGRATION_ENABLED이 false면 empty 반환 되어서 orElseThrow
                .map(info -> Stock.create(sku, info.itemName(), info.itemUnit(), warehouseId, 0, info.safetyStock()))
                .orElseThrow(() -> new ItemNotFoundException(sku));
    }
}
