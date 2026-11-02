package com.projectmgmt.tech;

import java.util.Map;

public record UpsertTechRecord(boolean isPublic, Map<String, Object> extra) {
}
