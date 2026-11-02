package com.projectmgmt.project;

import java.time.LocalDateTime;
import java.util.List;

public record ProjectProgressView(
        Long id,
        Long projectId,
        String projectName,
        String projectCode,
        String content,
        int percent,
        String submitSource,
        String submitSourceLabel,
        String creatorName,
        List<AttachmentView> attachments,
        LocalDateTime createdAt
) {
    public static ProjectProgressView from(ProjectProgress progress) {
        Project project = progress.getProject();
        String source = progress.getSubmitSource() == null ? "ADMIN" : progress.getSubmitSource();
        return new ProjectProgressView(
                progress.getId(),
                project.getId(),
                project.getName(),
                project.getCode(),
                progress.getContent(),
                progress.getPercent(),
                source,
                "PUBLIC".equals(source) ? "公开" : "内部",
                progress.getCreatorName(),
                progress.getFiles().stream()
                        .map(file -> new AttachmentView(
                                file.getId(),
                                file.getOriginalName(),
                                file.getUrl(),
                                file.getSize(),
                                file.getContentType()
                        ))
                        .toList(),
                progress.getCreatedAt()
        );
    }
}
