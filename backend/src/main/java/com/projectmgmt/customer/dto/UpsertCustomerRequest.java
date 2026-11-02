package com.projectmgmt.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpsertCustomerRequest(
        @NotBlank(message = "客户简称不能为空")
        @Size(max = 50, message = "客户简称最多 50 字")
        String shortName,

        @NotBlank(message = "公司名称不能为空")
        @Size(max = 120, message = "公司名称最多 120 字")
        String companyName,

        @NotBlank(message = "客户编码不能为空")
        @Size(max = 32, message = "客户编码最多 32 字")
        String code,

        @NotBlank(message = "请选择客户等级")
        @Size(max = 20, message = "客户等级最多 20 字")
        String level,

        @Valid
        List<ContactRequest> contacts,

        @Valid
        List<BankAccountRequest> bankAccounts,

        @JsonProperty("isPublic")
        Boolean isPublic
) {
}
