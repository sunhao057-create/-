package com.projectmgmt.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BankAccountView(
        Long id,
        String name,
        String bankName,
        String accountName,
        String accountNo,
        String branch,
        BigDecimal openingBalance,
        BigDecimal incomeAmount,
        BigDecimal expenseAmount,
        BigDecimal balance,
        String remark,
        boolean enabled,
        LocalDateTime createdAt
) {
    public static BankAccountView from(BankAccount account, BigDecimal income, BigDecimal expense) {
        BigDecimal opening = account.getOpeningBalance() == null ? BigDecimal.ZERO : account.getOpeningBalance();
        BigDecimal in = income == null ? BigDecimal.ZERO : income;
        BigDecimal out = expense == null ? BigDecimal.ZERO : expense;
        return new BankAccountView(
                account.getId(),
                account.getName(),
                account.getBankName(),
                account.getAccountName(),
                account.getAccountNo(),
                account.getBranch(),
                opening,
                in,
                out,
                opening.add(in).subtract(out),
                account.getRemark(),
                account.isEnabled(),
                account.getCreatedAt()
        );
    }
}
