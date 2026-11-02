package com.projectmgmt.warehouse;

public record UpsertStockOutLineRequest(
        String itemName,
        String qty,
        String unit,
        String amount
) {
}
