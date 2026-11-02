package com.projectmgmt.analytics;

import java.math.BigDecimal;
import java.util.List;

public record AnalyticsView(
        Kpi kpis,
        List<CustomerRow> customers,
        List<ContractRow> contracts,
        List<ProjectRow> projects,
        List<MonthRow> monthly
) {
    public record Kpi(
            long customerCount,
            long contractCount,
            BigDecimal contractAmount,
            BigDecimal collectedAmount,
            BigDecimal outstandingAmount,
            long projectCount,
            BigDecimal paymentAmount,
            Double avgProgress,
            Double collectionRate,
            long executingContractCount,
            long overdueContractCount
    ) {
    }

    public record CustomerRow(
            Long customerId,
            String customerName,
            long contractCount,
            BigDecimal contractAmount,
            BigDecimal collectedAmount,
            BigDecimal outstandingAmount,
            long projectCount,
            Double avgProgress
    ) {
    }

    public record ContractRow(
            Long contractId,
            String contractCode,
            String contractName,
            Long customerId,
            String customerName,
            String statusLabel,
            BigDecimal amount,
            BigDecimal collectedAmount,
            BigDecimal outstandingAmount,
            long projectCount,
            String signDate
    ) {
    }

    public record ProjectRow(
            Long projectId,
            String projectName,
            String projectCode,
            Long customerId,
            String customerName,
            Long contractId,
            String contractCode,
            String contractName,
            Integer progressPercent,
            BigDecimal contractAmount,
            BigDecimal collectedAmount,
            BigDecimal paymentAmount
    ) {
    }

    public record MonthRow(
            String month,
            BigDecimal contractAmount,
            BigDecimal collectedAmount,
            BigDecimal paymentAmount
    ) {
    }
}
