package com.projectmgmt.purchase;

import java.util.List;

public record UpsertPurchaseRequest(
        Long projectId,
        Long supplierId,
        Long warehouseId,
        String itemName,
        String qty,
        String unit,
        String amount,
        String reason,
        String purchaseDate,
        List<UpsertPurchaseLineRequest> lines
) {
}
