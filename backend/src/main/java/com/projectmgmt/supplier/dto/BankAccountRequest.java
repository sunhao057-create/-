package com.projectmgmt.supplier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BankAccountRequest(
        @NotBlank(message = "开户名不能为空")
        @Size(max = 80, message = "开户名最多 80 字")
        String accountName,

        @NotBlank(message = "开户银行不能为空")
        @Size(max = 80, message = "开户银行最多 80 字")
        String bankName,

        @NotBlank(message = "银行账号不能为空")
        @Size(max = 40, message = "银行账号最多 40 字")
        String accountNo,

        @Size(max = 80, message = "开户支行最多 80 字")
        String branch,

        Boolean primaryAccount
) {
}
