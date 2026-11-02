package com.projectmgmt.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BankTxnView(
        Long id,
        Long accountId,
        String accountName,
        LocalDate txnDate,
        String direction,
        String directionLabel,
        BigDecimal amount,
        String summary,
        String sourceType,
        Long sourceId,
        Long projectId,
        String projectName,
        LocalDateTime createdAt
) {
    public static BankTxnView from(BankTxn txn) {
        return new BankTxnView(
                txn.getId(),
                txn.getAccount().getId(),
                txn.getAccount().getName(),
                txn.getTxnDate(),
                txn.getDirection().name(),
                txn.getDirection().label(),
                txn.getAmount(),
                txn.getSummary(),
                txn.getSourceType().name(),
                txn.getSourceId(),
                txn.getProject() == null ? null : txn.getProject().getId(),
                txn.getProject() == null ? null : txn.getProject().getName(),
                txn.getCreatedAt()
        );
    }
}
