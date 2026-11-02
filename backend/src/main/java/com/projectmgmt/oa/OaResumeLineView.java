package com.projectmgmt.oa;

public record OaResumeLineView(
        Long id,
        String title,
        String subtitle,
        String extra,
        String startYm,
        String endYm,
        int sortOrder
) {
    public static OaResumeLineView from(OaResumeLine line) {
        return new OaResumeLineView(
                line.getId(),
                line.getTitle(),
                line.getSubtitle(),
                line.getExtra(),
                line.getStartYm(),
                line.getEndYm(),
                line.getSortOrder()
        );
    }
}
