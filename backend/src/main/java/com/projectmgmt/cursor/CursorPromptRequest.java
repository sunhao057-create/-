package com.projectmgmt.cursor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CursorPromptRequest(
        @NotBlank(message = "请填写需求")
        @Size(max = 4000, message = "需求最多 4000 字")
        String prompt
) {
}
