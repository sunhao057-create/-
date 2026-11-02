package com.projectmgmt.portal;

public record PortalPostView(
        Long id,
        String kind,
        String category,
        String title,
        String summary,
        String body,
        String coverUrl,
        int sortOrder,
        boolean enabled,
        String createdAt) {

    public static PortalPostView from(PortalPost post) {
        return new PortalPostView(
                post.getId(),
                post.getKind(),
                post.getCategory(),
                post.getTitle(),
                post.getSummary(),
                post.getBody(),
                post.getCoverUrl(),
                post.getSortOrder(),
                post.isEnabled(),
                post.getCreatedAt() == null ? null : post.getCreatedAt().toString());
    }
}
