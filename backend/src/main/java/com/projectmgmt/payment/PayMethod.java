package com.projectmgmt.payment;

public enum PayMethod {
    WIRE("电汇"),
    CHECK("支票"),
    CASH("现金"),
    OTHER("其他");

    private final String label;

    PayMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static PayMethod from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw com.projectmgmt.common.ApiException.badRequest("请选择付款方式");
        }
        String value = raw.trim();
        for (PayMethod method : values()) {
            if (method.name().equalsIgnoreCase(value) || method.label.equals(value)) {
                return method;
            }
        }
        throw com.projectmgmt.common.ApiException.badRequest("付款方式只能是 电汇 / 支票 / 现金 / 其他");
    }
}
