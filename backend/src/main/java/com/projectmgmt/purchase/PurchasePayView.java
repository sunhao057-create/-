package com.projectmgmt.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PurchasePayView(
        Long id,
        String code,
        Long purchaseId,
        String purchaseCode,
        Long projectId,
        String projectName,
        Long accountId,
        String accountName,
        BigDecimal amount,
        String reason,
        LocalDate payDate,
        boolean ledgerPosted,
        BigDecimal unpaidAmount,
        String workflowStatus,
        String workflowStatusLabel,
        LocalDateTime createdAt
) {
}
