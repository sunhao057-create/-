package com.projectmgmt.finance;

import com.projectmgmt.common.ApiException;

public enum ExpenseCategory {
    TRAVEL("差旅"),
    ENTERTAIN("招待"),
    TRANSPORT("交通"),
    OFFICE("办公"),
    COMMUNICATION("通讯"),
    OTHER("其他");

    private final String label;

    ExpenseCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ExpenseCategory from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("请选择报销类别");
        }
        String value = raw.trim().toUpperCase();
        for (ExpenseCategory item : values()) {
            if (item.name().equals(value) || item.label.equals(raw.trim())) {
                return item;
            }
        }
        throw ApiException.badRequest("报销类别无效");
    }
}
