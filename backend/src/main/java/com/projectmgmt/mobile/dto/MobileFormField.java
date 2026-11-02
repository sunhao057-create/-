package com.projectmgmt.mobile.dto;

import java.util.List;

public record MobileFormField(
        String key,
        String label,
        String type,
        boolean required,
        String extra,
        String saveKey,
        String lookup,
        List<MobileFormOption> options,
        List<MobileFormField> columns,
        String hint,
        String fileKey
) {}
