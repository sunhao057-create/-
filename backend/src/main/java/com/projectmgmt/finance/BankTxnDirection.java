package com.projectmgmt.finance;

import com.projectmgmt.common.ApiException;

public enum BankTxnDirection {
    IN("收入"),
    OUT("支出");

    private final String label;

    BankTxnDirection(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static BankTxnDirection from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("请选择收支方向");
        }
        String value = raw.trim().toUpperCase();
        if ("IN".equals(value) || "收入".equals(raw.trim())) {
            return IN;
        }
        if ("OUT".equals(value) || "支出".equals(raw.trim())) {
            return OUT;
        }
        throw ApiException.badRequest("收支方向无效");
    }
}
