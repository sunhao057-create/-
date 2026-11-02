package com.projectmgmt.warehouse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record WarehouseView(
        Long id,
        String name,
        String address,
        String keeper,
        String remark,
        boolean enabled,
        BigDecimal stockQty,
        LocalDateTime createdAt
) {
    public static WarehouseView from(Warehouse warehouse, BigDecimal stockQty) {
        return new WarehouseView(
                warehouse.getId(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.getKeeper(),
                warehouse.getRemark(),
                warehouse.isEnabled(),
                stockQty == null ? BigDecimal.ZERO : stockQty,
                warehouse.getCreatedAt()
        );
    }
}
