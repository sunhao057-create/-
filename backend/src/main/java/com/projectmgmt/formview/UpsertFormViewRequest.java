package com.projectmgmt.formview;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public record UpsertFormViewRequest(
        @NotBlank(message = "请填写视图标题")
        String name,
        @NotBlank(message = "请选择视图类型")
        String type,
        String remark,
        Boolean pinTop,
        String visibility,
        String dataRange,
        String rowHeight,
        String sortField,
        String sortDir,
        Boolean searchEnabled,
        Boolean queryEnabled,
        List<String> fields,
        Map<String, Object> extra
) {
}
