package com.projectmgmt.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseAnalyticsView(
        Kpi kpis,
        List<SupplierRow> suppliers,
        List<MonthRow> monthly,
        List<RecentRow> recent
) {
    public record Kpi(
            long purchaseCount,
            BigDecimal purchaseAmount,
            BigDecimal paidAmount,
            BigDecimal unpaidAmount,
            BigDecimal linkedAmount,
            BigDecimal unlinkedAmount
    ) {
    }

    public record SupplierRow(
            Long supplierId,
            String supplierName,
            long purchaseCount,
            BigDecimal purchaseAmount,
            BigDecimal unpaidAmount
    ) {
    }

    public record MonthRow(String month, BigDecimal purchaseAmount, BigDecimal paidAmount) {
    }

    public record RecentRow(
            Long id,
            String code,
            String itemName,
            String supplierName,
            LocalDate purchaseDate,
            BigDecimal amount,
            BigDecimal unpaidAmount,
            boolean linked
    ) {
    }
}
