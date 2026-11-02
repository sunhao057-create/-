package com.projectmgmt.purchase;

public record UpsertPurchasePayRequest(
        Long purchaseId,
        Long accountId,
        String amount,
        String reason,
        String payDate
) {
}
