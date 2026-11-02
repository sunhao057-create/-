package com.projectmgmt.portal;

public record PublicModuleFlags(boolean publicAccess, boolean display, boolean submit, boolean query, boolean export) {
    public static PublicModuleFlags closed() {
        return new PublicModuleFlags(false, false, false, false, false);
    }
}
