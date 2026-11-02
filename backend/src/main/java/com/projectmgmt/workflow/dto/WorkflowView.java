package com.projectmgmt.workflow.dto;

import com.projectmgmt.workflow.WorkflowGraph;
import java.time.LocalDateTime;
import java.util.List;

public record WorkflowView(
        Long id,
        String name,
        String code,
        String formModule,
        String formModuleLabel,
        boolean published,
        List<WorkflowGraph.WorkflowNode> nodes,
        List<WorkflowGraph.WorkflowEdge> edges,
        LocalDateTime updatedAt
) {
}
