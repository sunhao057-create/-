package com.projectmgmt.contract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CollectionView(
        Long id,
        String code,
        Long customerId,
        String customerShortName,
        Long contractId,
        String contractCode,
        String contractName,
        BigDecimal contractAmount,
        BigDecimal collectedAmount,
        BigDecimal outstandingAmount,
        Long projectId,
        String projectName,
        String projectCode,
        Long accountId,
        String accountName,
        BigDecimal amount,
        LocalDate receivedDate,
        String method,
        String remark,
        LocalDateTime createdAt
) {
    public static CollectionView from(ContractCollection item, BigDecimal collected) {
        var contract = item.getContract();
        var customer = contract.getCustomer();
        BigDecimal amount = contract.getAmount() == null ? BigDecimal.ZERO : contract.getAmount();
        BigDecimal paid = collected == null ? BigDecimal.ZERO : collected;
        return new CollectionView(
                item.getId(),
                item.getCode(),
                customer.getId(),
                customer.getShortName(),
                contract.getId(),
                contract.getCode(),
                contract.getName(),
                amount,
                paid,
                amount.subtract(paid),
                item.getProject() == null ? null : item.getProject().getId(),
                item.getProject() == null ? null : item.getProject().getName(),
                item.getProject() == null ? null : item.getProject().getCode(),
                item.getAccount() == null ? null : item.getAccount().getId(),
                item.getAccount() == null ? null : item.getAccount().getName(),
                item.getAmount(),
                item.getReceivedDate(),
                item.getMethod(),
                item.getRemark(),
                item.getCreatedAt()
        );
    }

    public static CollectionView from(ContractCollection item) {
        return from(item, item.getAmount());
    }
}
