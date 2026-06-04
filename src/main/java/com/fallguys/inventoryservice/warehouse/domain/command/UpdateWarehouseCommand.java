package com.fallguys.inventoryservice.warehouse.domain.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fallguys.inventoryservice.shared.exception.InvalidParameterException;
import com.fallguys.inventoryservice.shared.exception.ParameterViolation;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseCodeImmutableException;
import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;

/**
 * 창고 수정 유스케이스 입력. 변경 가능 항목(name·type·branchId·address)과 낙관적 락용 version을 받는다.
 * code는 불변이라 요청에 포함되면 거부하고, type 문자열은 enum으로 파싱한다.
 * (필수값·길이·version 존재는 표현 계층 @Valid가, 유형↔branchId 정합은 도메인이 검증한다.)
 */
public record UpdateWarehouseCommand(
        String name,
        WarehouseType type,
        Long branchId,
        String address,
        Long version
) {

    /**
     * 원시 입력을 검증하여 Command를 만든다.
     *
     * @throws WarehouseCodeImmutableException 요청에 code가 포함됐을 때(400, 식별자 불변)
     * @throws InvalidParameterException       type가 허용치 밖일 때(400, details 포함)
     */
    public static UpdateWarehouseCommand of(String code, String name, String type,
                                            Long branchId, String address, Long version) {
        if (code != null) {
            throw new WarehouseCodeImmutableException();
        }
        List<ParameterViolation> violations = new ArrayList<>();
        WarehouseType parsedType = parseType(type, violations);
        if (!violations.isEmpty()) {
            throw new InvalidParameterException(violations);
        }
        return new UpdateWarehouseCommand(name, parsedType, branchId, address, version);
    }

    private static WarehouseType parseType(String type, List<ParameterViolation> violations) {
        if (type != null) {
            for (WarehouseType candidate : WarehouseType.values()) {
                if (candidate.name().equalsIgnoreCase(type)) {
                    return candidate;
                }
            }
        }
        violations.add(new ParameterViolation("type", type, // 올바르지 않은 type 값 들어왔을 때
                Arrays.stream(WarehouseType.values()).map(Enum::name).toList()));
        return null;
    }
}
