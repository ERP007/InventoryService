package com.fallguys.inventoryservice.stock.domain;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fallguys.inventoryservice.stock.domain.command.AdjustStockCommand;
import com.fallguys.inventoryservice.stock.domain.exception.StockNotFoundException;
import com.fallguys.inventoryservice.stock.domain.query.StockAdjustmentResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockAdjustmentService {

    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;

    /**
     * 재고를 조정하고 이동 이력(append-only) 1건을 남긴다. ADMIN·HQ_MANAGER 전용(인가는 컨트롤러).
     *
     * 흐름:
     * 1) (sku × warehouseCode) 재고를 조회한다. 없으면 404(StockNotFoundException) — 모든 유형 공통.
     * 2) 도메인이 변동량을 계산·반영한다(INCREASE +, DECREASE -, ADJUST 실측-현재고).
     *    변동이 0이면 NO_STOCK_CHANGE(400), 차감 결과가 음수면 INSUFFICIENT_STOCK(409)로 거부한다.
     * 3) 변경된 재고를 저장하고(같은 영속 컨텍스트의 엔티티에 반영 → @Version 낙관락 적용),
     *    같은 변동으로 StockMovement를 1건 생성·저장한다.
     *
     * 트랜잭션: 쓰기. 2단계의 도메인 예외는 저장 이전에 발생해 아무것도 저장되지 않는다.
     * 동시 조정으로 버전 충돌 시 커밋 시점에 낙관락 예외가 발생해 전체 롤백된다(409 매핑).
     *
     * 예외:
     * - 재고 없음: StockNotFoundException (404)
     * - 변동 없음(주로 실측=현재고): NoStockChangeException (400)
     * - 차감량 > 가용재고: InsufficientStockException (409)
     */
    @Transactional
    public StockAdjustmentResult adjust(AdjustStockCommand command) {
        Stock stock = stockRepository.findBySkuAndWarehouseCode(command.sku(), command.warehouseCode())
                .orElseThrow(() -> new StockNotFoundException(command.warehouseCode(), command.sku()));

        int previousQuantity = stock.getQuantity();
        int delta = stock.adjust(command.adjustmentType(), command.quantity());
        stockRepository.save(stock);

        StockMovement movement = StockMovement.createAdjustment(
                command.sku(), stock.getItemName(), stock.getItemUnit(), stock.getWarehouseId(), delta,
                command.adjustmentType().toMovementType(), command.reason(), stock.getQuantity(),
                command.note(), command.executorEmpNo(), command.executorName());
        StockMovement saved = stockMovementRepository.save(movement);

        return new StockAdjustmentResult(
                saved.getId(), stock.getId(), command.sku(), command.warehouseCode(),
                previousQuantity, delta, stock.getQuantity(), stock.getSafetyStock(), saved.getPerformedAt());
    }
}
