package com.projectmgmt.org;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertDepartmentRequest(
        @NotBlank(message = "请填写部门名称")
        @Size(max = 80, message = "部门名称最多 80 字")
        String name,
        Long parentId,
        Integer sortOrder
) {
}
