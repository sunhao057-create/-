package com.projectmgmt.warehouse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record StockOutView(
        Long id,
        String code,
        String kind,
        Long warehouseId,
        String warehouseName,
        Long targetWarehouseId,
        String targetWarehouseName,
        Long purchaseId,
        String purchaseCode,
        Long projectId,
        String projectName,
        String projectCode,
        String itemName,
        BigDecimal qty,
        String unit,
        BigDecimal amount,
        String reason,
        LocalDate outDate,
        boolean ledgerPosted,
        boolean budgetDeducted,
        BigDecimal onHand,
        BigDecimal availableAmount,
        String workflowStatus,
        String workflowStatusLabel,
        List<StockOutLineView> lines,
        LocalDateTime createdAt
) {
}
