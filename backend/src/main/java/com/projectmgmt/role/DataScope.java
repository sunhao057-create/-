package com.projectmgmt.role;

public enum DataScope {
    ALL,
    OWN;

    public static DataScope from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        String normalized = value.trim().toUpperCase();
        if ("OWN".equals(normalized) || "SELF".equals(normalized)) {
            return OWN;
        }
        try {
            return DataScope.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return ALL;
        }
    }
}
