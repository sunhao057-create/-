package com.projectmgmt.oa;

import java.math.BigDecimal;

public record OaLoanView(
        Boolean linkProject,
        Long projectId,
        String projectName,
        String projectCode,
        Long customerId,
        String customerName,
        String belongCompany,
        BigDecimal amount,
        BigDecimal payAmount,
        Long accountId,
        String accountName,
        String accountNo,
        String bankName,
        Long loanRequestId,
        String loanCode,
        BigDecimal repayAmount,
        BigDecimal paidTotal,
        BigDecimal pendingPayTotal,
        BigDecimal remainingPay,
        BigDecimal repayTotal,
        BigDecimal pendingRepayTotal,
        BigDecimal remainingRepay,
        String repayStatus,
        String loanStatus
) {
}
