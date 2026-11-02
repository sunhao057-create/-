package com.projectmgmt.export;

import java.util.List;

public record ListExportRequest(
        String module,
        List<Long> ids,
        String keyword,
        String publicFilter,
        String status,
        Long customerId
) {}
