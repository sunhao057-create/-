package com.projectmgmt.portal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublicProjectSubmitRequest(
        @NotBlank(message = "项目名称不能为空")
        @Size(max = 200, message = "项目名称最多 200 字")
        String name,

        @Size(max = 50, message = "项目编码最多 50 字")
        String code,

        Long customerId,

        @Size(max = 120, message = "公司名称最多 120 字")
        String companyName,

        Long managerId,

        Long regionId,

        @Size(max = 2000, message = "概况最多 2000 字")
        String overview,

        @Size(max = 50, message = "联系人最多 50 字")
        String contactName,

        @Size(max = 30, message = "联系电话最多 30 字")
        String contactPhone
) {}
