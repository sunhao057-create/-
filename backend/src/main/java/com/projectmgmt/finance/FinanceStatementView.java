package com.projectmgmt.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FinanceStatementView(
        List<FinanceAnalyticsView.AccountRow> accounts,
        Long accountId,
        String accountName,
        String bankName,
        String accountNo,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        BigDecimal monthIncome,
        BigDecimal monthExpense,
        List<FinanceAnalyticsView.MonthRow> monthly,
        List<Line> lines
) {
    public record Line(
            Long id,
            LocalDate txnDate,
            String summary,
            String projectName,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal balance,
            String direction,
            String sourceType,
            Long sourceId
    ) {
    }
}
