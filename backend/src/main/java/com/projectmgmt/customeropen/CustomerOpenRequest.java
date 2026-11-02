package com.projectmgmt.customeropen;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerOpenRequest(
        @NotBlank(message = "请填写公司名称")
        @Size(max = 80, message = "公司名称最多 80 字")
        String companyName,
        @Size(max = 50, message = "客户简称最多 50 字")
        String shortName,
        @Size(max = 20, message = "客户等级最多 20 字")
        String level,
        @Size(max = 50, message = "联系人最多 50 字")
        String contactName,
        @Size(max = 30, message = "联系电话最多 30 字")
        String contactPhone,
        @Size(max = 200, message = "单位地址最多 200 字")
        String address
) {
}
