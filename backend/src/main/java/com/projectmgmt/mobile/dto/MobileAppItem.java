package com.projectmgmt.mobile.dto;

public record MobileAppItem(
        String module,
        String label,
        String path,
        String createPath,
        String tone,
        boolean canCreate
) {
}
