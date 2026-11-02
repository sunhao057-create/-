package com.projectmgmt.portal;

import java.util.List;

public record PublicCustomerView(
        Long id,
        String shortName,
        String companyName,
        String code,
        String level,
        List<PublicContactView> contacts,
        List<PublicBankAccountView> bankAccounts
) {}
