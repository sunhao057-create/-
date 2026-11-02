package com.projectmgmt.purchase;

public record UpsertPurchaseLineRequest(
        String itemName,
        String qty,
        String unit,
        String amount
) {
}
