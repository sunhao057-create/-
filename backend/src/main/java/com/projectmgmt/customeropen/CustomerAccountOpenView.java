package com.projectmgmt.customeropen;

public record CustomerAccountOpenView(
        Long id,
        String code,
        Long customerId,
        String companyName,
        Long departmentId,
        String departmentName,
        String displayName,
        String username,
        String phone,
        String accountType,
        String roleCode,
        String roleName,
        String initialPassword,
        Long userId,
        boolean settled,
        String workflowStatus,
        String workflowStatusLabel
) {
}
