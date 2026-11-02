package com.projectmgmt.finance;

import java.math.BigDecimal;
import java.util.List;

public record FinanceAnalyticsView(
        Kpi kpis,
        List<AccountRow> accounts,
        List<MonthRow> monthly,
        List<CategoryRow> categories,
        List<ProjectSpendRow> projects,
        List<BankTxnView> recent
) {
    public record Kpi(
            long accountCount,
            BigDecimal totalBalance,
            BigDecimal monthIncome,
            BigDecimal monthExpense,
            BigDecimal expenseAmount,
            BigDecimal projectLinkedExpense,
            BigDecimal unlinkedExpense
    ) {
    }

    public record AccountRow(
            Long accountId,
            String name,
            String bankName,
            BigDecimal openingBalance,
            BigDecimal incomeAmount,
            BigDecimal expenseAmount,
            BigDecimal balance
    ) {
    }

    public record MonthRow(String month, BigDecimal incomeAmount, BigDecimal expenseAmount) {
    }

    public record CategoryRow(String category, String categoryLabel, BigDecimal amount, long count) {
    }

    public record ProjectSpendRow(
            Long projectId,
            String projectName,
            String projectCode,
            BigDecimal availableAmount,
            BigDecimal paymentAmount,
            BigDecimal expenseAmount,
            BigDecimal spendAmount
    ) {
    }
}
