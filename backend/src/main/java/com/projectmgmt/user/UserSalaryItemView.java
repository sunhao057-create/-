package com.projectmgmt.user;

import java.math.BigDecimal;

public record UserSalaryItemView(Long id, String itemName, BigDecimal amount, int sortOrder) {
    public static UserSalaryItemView from(UserSalaryItem item) {
        return new UserSalaryItemView(item.getId(), item.getItemName(), item.getAmount(), item.getSortOrder());
    }
}
