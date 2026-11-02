package com.projectmgmt.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.projectmgmt.customer.Customer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ContractView(
        Long id,
        String code,
        String name,
        Long customerId,
        String customerName,
        String customerShortName,
        BigDecimal amount,
        LocalDate signDate,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String statusLabel,
        String remark,
        int projectCount,
        BigDecimal collectedAmount,
        BigDecimal outstandingAmount,
        LocalDateTime createdAt,
        @JsonProperty("isPublic") boolean isPublic
) {
    public static ContractView from(Contract contract, int projectCount, BigDecimal collected) {
        BigDecimal amount = contract.getAmount() == null ? BigDecimal.ZERO : contract.getAmount();
        BigDecimal collectedAmount = collected == null ? BigDecimal.ZERO : collected;
        Customer customer = contract.getCustomer();
        return new ContractView(
                contract.getId(),
                contract.getCode(),
                contract.getName(),
                customer == null ? null : customer.getId(),
                customer == null ? null : customer.getCompanyName(),
                customer == null ? null : customer.getShortName(),
                amount,
                contract.getSignDate(),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getStatus() == null ? ContractStatus.DRAFT.name() : contract.getStatus().name(),
                contract.getStatus() == null ? ContractStatus.DRAFT.label() : contract.getStatus().label(),
                contract.getRemark(),
                projectCount,
                collectedAmount,
                amount.subtract(collectedAmount),
                contract.getCreatedAt(),
                contract.isPublic()
        );
    }
}
