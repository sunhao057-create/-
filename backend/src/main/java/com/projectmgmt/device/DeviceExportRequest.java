package com.projectmgmt.device;

import java.util.List;

public record DeviceExportRequest(
        String keyword,
        Long departmentId,
        Boolean enabled,
        String code,
        String name,
        String model,
        String location,
        List<Long> ids
) {
}
