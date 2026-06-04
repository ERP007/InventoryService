package com.fallguys.inventoryservice.domain.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fallguys.inventoryservice.domain.exception.InvalidParameterException;
import com.fallguys.inventoryservice.domain.exception.ParameterViolation;
import com.fallguys.inventoryservice.domain.model.WarehouseType;

/**
 * 창고 등록 유스케이스 입력. 상태 변경이므로 Command record로 받는다.
 * type 문자열을 enum으로 파싱하며, 허용치 밖이면 INVALID_PARAMETER로 보고한다.
 * (필수값 누락·길이는 표현 계층 @Valid가, 유형↔branchId 정합은 도메인이 검증한다.)
 */
public record CreateWarehouseCommand(
        String code,
        String name,
        WarehouseType type,
        Long branchId,
        String address
) {

    /**
     * 원시 입력을 검증하여 Command를 만든다. type이 HQ/DEALER가 아니면 InvalidParameterException(400).
     *
     * @throws InvalidParameterException type가 허용치 밖일 때(details에 field·value·allowed 포함)
     */
    public static CreateWarehouseCommand of(String code, String name, String type, Long branchId, String address) {
        List<ParameterViolation> violations = new ArrayList<>();
        WarehouseType parsedType = parseType(type, violations);

        // 현재는 type 관련 exception 만 존재, branchId 와 type 의 유효성은 Warehouse 에서 검증
        if (!violations.isEmpty()) {
            throw new InvalidParameterException(violations);
        }
        return new CreateWarehouseCommand(code, name, parsedType, branchId, address);
    }

    private static WarehouseType parseType(String type, List<ParameterViolation> violations) {
        if (type != null) {
            for (WarehouseType candidate : WarehouseType.values()) {
                if (candidate.name().equalsIgnoreCase(type)) {
                    return candidate;
                }
            }
        }
        violations.add(new ParameterViolation("type", type,
                Arrays.stream(WarehouseType.values()).map(Enum::name).toList()));
        return null;
    }
}
