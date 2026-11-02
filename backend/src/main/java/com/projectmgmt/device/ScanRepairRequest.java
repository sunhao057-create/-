package com.projectmgmt.device;

public record ScanRepairRequest(
        String grade,
        String customerName,
        String customerAddress,
        String contactName,
        String contactPhone,
        String instrumentName,
        String instrumentModel,
        String brand,
        String fault,
        String clientOrg,
        String accountManager,
        String remark
) {
}
