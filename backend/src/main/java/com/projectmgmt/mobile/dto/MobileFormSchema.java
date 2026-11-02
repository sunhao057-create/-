package com.projectmgmt.mobile.dto;

import java.util.List;

public record MobileFormSchema(
        String module,
        String title,
        String family,
        String slug,
        String saveUrl,
        String saveEncoding,
        boolean canCreate,
        boolean canEdit,
        List<MobileFormField> fields
) {}
