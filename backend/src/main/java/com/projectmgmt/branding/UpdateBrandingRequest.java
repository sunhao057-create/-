package com.projectmgmt.branding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBrandingRequest(
        @NotBlank(message = "系统名称不能为空")
        @Size(max = 50, message = "系统名称最多 50 字")
        String name
) {
}
