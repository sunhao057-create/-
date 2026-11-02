package com.projectmgmt.formview;

import java.util.List;
import java.util.Map;

public record FormViewDto(
        Long id,
        String module,
        String name,
        String type,
        String remark,
        boolean pinTop,
        String visibility,
        String dataRange,
        String rowHeight,
        String sortField,
        String sortDir,
        boolean searchEnabled,
        boolean queryEnabled,
        List<String> fields,
        Map<String, Object> extra,
        boolean builtin,
        Long createdById,
        String createdByName
) {
}
