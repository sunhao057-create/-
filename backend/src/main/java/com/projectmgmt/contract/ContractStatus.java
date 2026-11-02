package com.projectmgmt.contract;

import com.projectmgmt.common.ApiException;

public enum ContractStatus {
    DRAFT("草稿"),
    SIGNED("已签订"),
    EXECUTING("执行中"),
    CLOSED("已关闭"),
    CANCELLED("已取消"),
    VOIDED("已作废");

    private final String label;

    ContractStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ContractStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            return DRAFT;
        }
        String value = raw.trim();
        for (ContractStatus status : values()) {
            if (status.name().equalsIgnoreCase(value) || status.label.equals(value)) {
                return status;
            }
        }
        throw ApiException.badRequest("合同状态不正确");
    }
}
