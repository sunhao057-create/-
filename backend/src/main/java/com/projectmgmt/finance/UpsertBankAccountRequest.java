package com.projectmgmt.finance;

import java.math.BigDecimal;

public record UpsertBankAccountRequest(
        String name,
        String bankName,
        String accountName,
        String accountNo,
        String branch,
        BigDecimal openingBalance,
        String remark,
        Boolean enabled
) {
}
