package com.projectmgmt.oa;

import java.math.BigDecimal;

public record OaSalaryLineView(Long id, String itemName, BigDecimal amount, int sortOrder) {
    public static OaSalaryLineView from(OaSalaryLine line) {
        return new OaSalaryLineView(line.getId(), line.getItemName(), line.getAmount(), line.getSortOrder());
    }
}
