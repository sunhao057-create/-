package com.projectmgmt.purchase;

import java.math.BigDecimal;

public record PurchaseLineView(
        Long id,
        String itemName,
        BigDecimal qty,
        String unit,
        BigDecimal amount,
        int sortOrder
) {
    public static PurchaseLineView from(PurchaseLine line) {
        return new PurchaseLineView(
                line.getId(),
                line.getItemName(),
                line.getQty(),
                line.getUnit(),
                line.getAmount(),
                line.getSortOrder()
        );
    }
}
