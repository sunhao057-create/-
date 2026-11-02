package com.projectmgmt.user;

import java.math.BigDecimal;
import java.util.List;

public record StaffView(
        Long id,
        String username,
        String displayName,
        String avatarUrl,
        Long departmentId,
        String departmentName,
        String position,
        String jobLevel,
        BigDecimal salary,
        List<UserSalaryItemView> salaryItems
) {
    public static StaffView from(SysUser user) {
        return new StaffView(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getDepartment() == null ? null : user.getDepartment().getId(),
                user.getDepartment() == null ? null : user.getDepartment().getName(),
                user.getPosition(),
                user.getJobLevel(),
                user.getSalary(),
                user.getSalaryItems() == null
                        ? List.of()
                        : user.getSalaryItems().stream().map(UserSalaryItemView::from).toList()
        );
    }
}
