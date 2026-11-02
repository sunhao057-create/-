package com.projectmgmt.project;

public record AttachmentView(Long id, String originalName, String url, long size, String contentType) {
    public static AttachmentView from(ProjectAttachment attachment) {
        return new AttachmentView(
                attachment.getId(),
                attachment.getOriginalName(),
                attachment.getUrl(),
                attachment.getSize(),
                attachment.getContentType()
        );
    }
}
