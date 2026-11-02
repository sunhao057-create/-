package com.projectmgmt.device;

public record UpsertDeviceWorkOrderRequest(
        Long deviceId,
        String reason,
        String applyDate,
        String targetLocation,
        Long targetDepartmentId,
        Long clientCustomerId,
        Long serviceCustomerId
) {
}
