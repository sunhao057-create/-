package com.projectmgmt.workflow.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record WorkflowRuntimeView(
        Long processId,
        String processName,
        String formModule,
        Long bizId,
        String currentNodeKey,
        String currentNodeName,
        String currentNodeType,
        String status,
        Map<String, String> fields,
        String nextNodeKey,
        String nextNodeName,
        boolean canAdvance,
        boolean canReturn,
        boolean canHandle,
        String returnReason,
        String returnFromName,
        List<PreviousNodeView> previousNodes,
        List<WorkflowHistoryView> history,
        List<String> currentAssignees,
        LocalDateTime dueAt,
        boolean overdue
) {
    public record PreviousNodeView(String key, String name) {
    }

    public record WorkflowHistoryView(
            String action,
            String actionLabel,
            String fromNodeName,
            String toNodeName,
            String actorName,
            String comment,
            LocalDateTime createdAt
    ) {
    }

    public String statusLabel() {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "DRAFT" -> "草稿";
            case "RUNNING" -> "审批中";
            case "DONE" -> "已完成";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }
}
