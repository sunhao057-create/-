package com.projectmgmt.user.dto;

import com.projectmgmt.user.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull(message = "请指定状态")
        UserStatus status
) {
}
