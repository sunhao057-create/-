package com.projectmgmt.warehouse;

import java.math.BigDecimal;

public record StockItemView(String itemName, String unit, BigDecimal stockQty) {
}
