package com.projectmgmt.workflow.dto;

import com.projectmgmt.workflow.FormFieldCatalog;

public record FormFieldView(String key, String label, boolean member, String parentLabel) {
    public static FormFieldView from(FormFieldCatalog.Field field) {
        return new FormFieldView(field.key(), field.label(), field.member(), field.parentLabel());
    }
}
