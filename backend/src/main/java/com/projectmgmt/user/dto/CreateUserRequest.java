package com.projectmgmt.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_]{4,32}$", message = "用户名为 4-32 位字母、数字或下划线")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码至少 8 位")
        String password,

        @NotBlank(message = "姓名不能为空")
        @Size(max = 50, message = "姓名最多 50 字")
        String displayName,

        @NotBlank(message = "请选择角色")
        String role,

        Long departmentId,

        Boolean deptLeader,

        String position,

        String jobLevel,

        String accountType
) {
}
