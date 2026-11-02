package com.projectmgmt.warehouse;

import java.math.BigDecimal;
import java.util.List;

public record WarehouseAnalyticsView(
        Kpi kpis,
        List<WarehouseStockRow> warehouses,
        List<ItemStockRow> items,
        List<MonthRow> monthly,
        List<StockTxnView> recent
) {
    public record Kpi(
            int warehouseCount,
            BigDecimal stockQty,
            BigDecimal monthInQty,
            BigDecimal monthOutQty,
            BigDecimal purchaseInQty,
            BigDecimal purchaseOutQty,
            BigDecimal projectOutQty
    ) {
    }

    public record WarehouseStockRow(Long warehouseId, String name, BigDecimal inQty, BigDecimal outQty, BigDecimal stockQty) {
    }

    public record ItemStockRow(String itemName, String unit, BigDecimal inQty, BigDecimal outQty, BigDecimal stockQty) {
    }

    public record MonthRow(String month, BigDecimal inQty, BigDecimal outQty) {
    }
}
