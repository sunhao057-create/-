package com.projectmgmt.branding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateThemeRequest(
        @NotBlank(message = "请选择主题风格")
        @Size(max = 32, message = "主题标识过长")
        String themeId
) {
}
