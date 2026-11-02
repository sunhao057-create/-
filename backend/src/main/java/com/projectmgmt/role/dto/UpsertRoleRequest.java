package com.projectmgmt.role.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpsertRoleRequest(
        @Size(max = 32, message = "角色编码最多 32 字")
        String code,

        @NotBlank(message = "角色名称不能为空")
        @Size(max = 50, message = "角色名称最多 50 字")
        String name,

        @Size(max = 200, message = "说明最多 200 字")
        String description,

        @Valid
        List<FormPermissionRequest> permissions
) {
}
