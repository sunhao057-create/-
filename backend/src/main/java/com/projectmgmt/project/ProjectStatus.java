package com.projectmgmt.project;

public enum ProjectStatus {
    INITIATED("已立项"),
    VOIDED("已作废");

    private final String label;

    ProjectStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
