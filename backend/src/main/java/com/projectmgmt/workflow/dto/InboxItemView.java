package com.projectmgmt.workflow.dto;

import java.time.LocalDateTime;

public record InboxItemView(
        Long id,
        String inboxType,
        String title,
        String formModule,
        String formModuleLabel,
        Long bizId,
        String processName,
        String nodeKey,
        String nodeName,
        String comment,
        String status,
        LocalDateTime createdAt,
        LocalDateTime handledAt,
        LocalDateTime dueAt,
        boolean overdue
) {
}
