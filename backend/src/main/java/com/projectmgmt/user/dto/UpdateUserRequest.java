package com.projectmgmt.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "姓名不能为空")
        @Size(max = 50, message = "姓名最多 50 字")
        String displayName,

        String role,

        Long departmentId,

        Boolean deptLeader,

        String position,

        String jobLevel,

        String accountType
) {
}
