package com.projectmgmt.portal;

public record PublicBankAccountView(
        Long id,
        String accountName,
        String bankName,
        String accountNo,
        String branch,
        boolean primaryAccount
) {}
