package com.projectmgmt.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReturnWorkflowRequest(
        @NotBlank String formModule,
        @NotNull Long bizId,
        @NotBlank String targetNodeKey,
        @NotBlank(message = "请填写退回原因")
        @Size(max = 500)
        String reason
) {
}
