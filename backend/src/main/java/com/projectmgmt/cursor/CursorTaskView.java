package com.projectmgmt.cursor;

import java.time.LocalDateTime;

public record CursorTaskView(
        Long id,
        String prompt,
        String status,
        String statusLabel,
        String agentId,
        String agentUrl,
        String prUrl,
        String branch,
        String resultText,
        String errorMessage,
        String createdByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static CursorTaskView from(CursorTask task) {
        return new CursorTaskView(
                task.getId(),
                task.getPrompt(),
                task.getStatus(),
                label(task.getStatus()),
                task.getAgentId(),
                task.getAgentUrl(),
                task.getPrUrl(),
                task.getBranch(),
                task.getResultText(),
                task.getErrorMessage(),
                task.getCreatedByName(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }

    private static String label(String status) {
        return switch (status == null ? "" : status) {
            case "RUNNING" -> "处理中";
            case "PUBLISHING" -> "发布中";
            case "PUBLISHED" -> "已发布";
            case "FAILED" -> "失败";
            default -> status;
        };
    }
}
