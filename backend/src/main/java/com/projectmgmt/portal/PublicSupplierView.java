package com.projectmgmt.portal;

import java.util.List;

public record PublicSupplierView(
        Long id,
        String shortName,
        String companyName,
        String code,
        String category,
        List<PublicContactView> contacts,
        List<PublicBankAccountView> bankAccounts
) {}
