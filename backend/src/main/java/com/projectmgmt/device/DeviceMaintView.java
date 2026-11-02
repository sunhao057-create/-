package com.projectmgmt.device;

import java.util.List;
import java.util.Map;

public record DeviceMaintView(
        Long id,
        String code,
        String service,
        String serviceLabel,
        String title,
        String customerName,
        String deviceCode,
        String instrumentName,
        String instrumentModel,
        String vendor,
        String maintainer,
        String startTime,
        String location,
        Map<String, Object> extra,
        String workflowStatus,
        String workflowStatusLabel,
        String creatorName,
        List<Map<String, Object>> files,
        String createdAt) {
}
