package com.projectmgmt.finance;

import java.math.BigDecimal;

public record UpsertBankTxnRequest(
        String txnDate,
        String direction,
        String amount,
        String summary,
        Long projectId
) {
}
