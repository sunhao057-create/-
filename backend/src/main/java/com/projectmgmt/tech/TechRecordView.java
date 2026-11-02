package com.projectmgmt.tech;

import java.util.List;
import java.util.Map;

public record TechRecordView(
        Long id,
        String code,
        String kind,
        String kindLabel,
        String title,
        Map<String, Object> extra,
        boolean isPublic,
        String submitSource,
        String creatorName,
        String workflowStatus,
        String workflowStatusLabel,
        List<Map<String, Object>> files,
        String createdAt,
        String updatedAt) {
}
