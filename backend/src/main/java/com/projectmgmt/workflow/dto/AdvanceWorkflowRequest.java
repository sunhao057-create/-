package com.projectmgmt.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdvanceWorkflowRequest(
        @NotBlank String formModule,
        @NotNull Long bizId,
        String comment
) {
}
