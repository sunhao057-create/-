package com.projectmgmt.portal;

import com.projectmgmt.supplier.dto.BankAccountRequest;
import com.projectmgmt.supplier.dto.ContactRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PublicSupplierSubmitRequest(
        @NotBlank(message = "供应商简称不能为空")
        @Size(max = 50, message = "供应商简称最多 50 字")
        String shortName,

        @NotBlank(message = "公司名称不能为空")
        @Size(max = 120, message = "公司名称最多 120 字")
        String companyName,

        @Size(max = 32, message = "供应商编码最多 32 字")
        String code,

        @Size(max = 20, message = "供应商类型最多 20 字")
        String category,

        @Size(max = 50, message = "联系人最多 50 字")
        String contactName,

        @Size(max = 30, message = "联系电话最多 30 字")
        String contactPhone,

        @Valid
        List<ContactRequest> contacts,

        @Valid
        List<BankAccountRequest> bankAccounts
) {}
