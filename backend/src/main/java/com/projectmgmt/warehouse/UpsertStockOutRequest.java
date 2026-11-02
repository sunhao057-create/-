package com.projectmgmt.warehouse;

import java.util.List;

public record UpsertStockOutRequest(
        Long warehouseId,
        Long targetWarehouseId,
        Long purchaseId,
        Long projectId,
        String itemName,
        String qty,
        String unit,
        String amount,
        String reason,
        String outDate,
        List<UpsertStockOutLineRequest> lines
) {
}
