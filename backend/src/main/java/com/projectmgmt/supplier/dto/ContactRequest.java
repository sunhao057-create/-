package com.projectmgmt.supplier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactRequest(
        @NotBlank(message = "联系人姓名不能为空")
        @Size(max = 50, message = "联系人姓名最多 50 字")
        String name,

        @Size(max = 50, message = "职务最多 50 字")
        String title,

        @Size(max = 30, message = "电话最多 30 字")
        String phone,

        @Size(max = 80, message = "邮箱最多 80 字")
        String email,

        Boolean primaryContact
) {
}
