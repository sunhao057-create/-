package com.projectmgmt.warehouse;

import com.projectmgmt.common.ApiException;

public enum StockTxnDirection {
    IN("入库"),
    OUT("出库");

    private final String label;

    StockTxnDirection(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static StockTxnDirection from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("请选择出入库方向");
        }
        String value = raw.trim().toUpperCase();
        if ("IN".equals(value) || "入库".equals(raw.trim())) {
            return IN;
        }
        if ("OUT".equals(value) || "出库".equals(raw.trim())) {
            return OUT;
        }
        throw ApiException.badRequest("出入库方向无效");
    }
}
