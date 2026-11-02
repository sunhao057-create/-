package com.projectmgmt.warehouse;

import java.math.BigDecimal;

public record StockOutLineView(
        Long id,
        String itemName,
        BigDecimal qty,
        String unit,
        BigDecimal amount,
        int sortOrder
) {
    public static StockOutLineView from(StockOutLine line) {
        return new StockOutLineView(
                line.getId(),
                line.getItemName(),
                line.getQty(),
                line.getUnit(),
                line.getAmount(),
                line.getSortOrder()
        );
    }
}
