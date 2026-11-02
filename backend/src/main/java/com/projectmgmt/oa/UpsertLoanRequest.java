package com.projectmgmt.oa;

import java.math.BigDecimal;

public record UpsertLoanRequest(
        Boolean linkProject,
        Long projectId,
        Long customerId,
        String belongCompany,
        BigDecimal amount,
        BigDecimal payAmount,
        Long accountId,
        Long loanRequestId,
        BigDecimal repayAmount
) {
}
