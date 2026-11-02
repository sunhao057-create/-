package com.projectmgmt.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PurchaseView(
        Long id,
        String code,
        Long projectId,
        String projectName,
        String projectCode,
        Long supplierId,
        String supplierName,
        Long warehouseId,
        String warehouseName,
        String itemName,
        BigDecimal qty,
        String unit,
        BigDecimal amount,
        String reason,
        LocalDate purchaseDate,
        boolean budgetDeducted,
        BigDecimal availableAmount,
        BigDecimal unpaidAmount,
        String workflowStatus,
        String workflowStatusLabel,
        List<PurchaseLineView> lines,
        LocalDateTime createdAt
) {
}
