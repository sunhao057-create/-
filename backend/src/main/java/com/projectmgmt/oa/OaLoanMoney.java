package com.projectmgmt.oa;

import java.math.BigDecimal;

public record OaLoanMoney(
        Boolean linkProject,
        Long projectId,
        Long customerId,
        BigDecimal amount,
        BigDecimal payAmount,
        Long accountId,
        Long loanRequestId,
        BigDecimal repayAmount
) {
}
