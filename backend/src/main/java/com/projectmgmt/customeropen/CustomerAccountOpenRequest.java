package com.projectmgmt.customeropen;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CustomerAccountOpenRequest(
        @NotNull(message = "请选择所属客户")
        Long customerId,
        @NotBlank(message = "请填写姓名")
        @Size(max = 50, message = "姓名最多 50 字")
        String displayName,
        @NotBlank(message = "请填写登录账号")
        @Size(max = 32, message = "登录账号最多 32 字")
        String username,
        @Size(max = 30, message = "手机号最多 30 字")
        String phone,
        String accountType,
        String roleCode,
        Long departmentId
) {
}
