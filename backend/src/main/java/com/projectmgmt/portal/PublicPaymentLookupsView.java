package com.projectmgmt.portal;

import java.util.List;

public record PublicPaymentLookupsView(
        List<PublicProjectOption> projects,
        List<PublicSupplierView> suppliers,
        List<PublicCompanyAccountView> accounts
) {}
