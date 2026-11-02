package com.projectmgmt.workflow.dto;

import com.projectmgmt.workflow.WorkflowGraph;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertWorkflowRequest(
        @NotBlank(message = "请填写流程名称")
        @Size(max = 80)
        String name,

        @NotBlank(message = "请填写流程编码")
        @Size(max = 32)
        String code,

        @NotBlank(message = "请选择绑定表单")
        String formModule,

        WorkflowGraph graph
) {
}
