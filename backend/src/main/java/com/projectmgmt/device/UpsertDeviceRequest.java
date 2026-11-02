package com.projectmgmt.device;

public record UpsertDeviceRequest(
        String name,
        String model,
        String brand,
        String serialNo,
        String location,
        String remark,
        Long departmentId,
        Long managerId,
        Boolean enabled
) {
}
