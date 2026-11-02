package com.projectmgmt.role.dto;

import jakarta.validation.constraints.NotBlank;

public record FormPermissionRequest(
        @NotBlank(message = "请选择表单")
        String module,
        Boolean view,
        Boolean create,
        Boolean edit,
        Boolean delete,
        Boolean importExport,
        Boolean flow,
        Boolean manageView,
        String dataScope,
        Boolean publicAccess,
        Boolean publicDisplay,
        Boolean publicSubmit,
        Boolean publicQuery,
        Boolean publicExport
) {
    public boolean viewEnabled() {
        return Boolean.TRUE.equals(view);
    }

    public boolean createEnabled() {
        return Boolean.TRUE.equals(create);
    }

    public boolean editEnabled() {
        return Boolean.TRUE.equals(edit);
    }

    public boolean deleteEnabled() {
        return Boolean.TRUE.equals(delete);
    }

    public boolean importExportEnabled() {
        return Boolean.TRUE.equals(importExport);
    }

    public boolean flowEnabled() {
        return Boolean.TRUE.equals(flow);
    }

    public boolean manageViewEnabled() {
        return Boolean.TRUE.equals(manageView);
    }

    public boolean publicAccessEnabled() {
        return Boolean.TRUE.equals(publicAccess);
    }

    public boolean publicDisplayEnabled() {
        return Boolean.TRUE.equals(publicDisplay);
    }

    public boolean publicSubmitEnabled() {
        return Boolean.TRUE.equals(publicSubmit);
    }

    public boolean publicQueryEnabled() {
        return Boolean.TRUE.equals(publicQuery);
    }

    public boolean publicExportEnabled() {
        return Boolean.TRUE.equals(publicExport);
    }
}
