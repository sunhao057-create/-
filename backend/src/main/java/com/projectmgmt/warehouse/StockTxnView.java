package com.projectmgmt.warehouse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record StockTxnView(
        Long id,
        Long warehouseId,
        String warehouseName,
        LocalDate txnDate,
        String direction,
        String directionLabel,
        String itemName,
        BigDecimal qty,
        String unit,
        BigDecimal amount,
        String summary,
        String sourceType,
        Long sourceId,
        Long projectId,
        String projectName,
        Long purchaseId,
        String purchaseCode,
        LocalDateTime createdAt
) {
    public static StockTxnView from(StockTxn txn) {
        return new StockTxnView(
                txn.getId(),
                txn.getWarehouse().getId(),
                txn.getWarehouse().getName(),
                txn.getTxnDate(),
                txn.getDirection().name(),
                txn.getDirection().label(),
                txn.getItemName(),
                txn.getQty(),
                txn.getUnit(),
                txn.getAmount(),
                txn.getSummary(),
                txn.getSourceType().name(),
                txn.getSourceId(),
                txn.getProject() == null ? null : txn.getProject().getId(),
                txn.getProject() == null ? null : txn.getProject().getName(),
                txn.getPurchase() == null ? null : txn.getPurchase().getId(),
                txn.getPurchase() == null ? null : txn.getPurchase().getCode(),
                txn.getCreatedAt()
        );
    }
}
