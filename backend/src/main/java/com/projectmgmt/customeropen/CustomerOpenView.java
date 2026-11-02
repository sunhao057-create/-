package com.projectmgmt.customeropen;

public record CustomerOpenView(
        Long id,
        String code,
        String companyName,
        String shortName,
        String level,
        String contactName,
        String contactPhone,
        String address,
        Long departmentId,
        Long customerId,
        boolean settled,
        String workflowStatus,
        String workflowStatusLabel
) {
}
