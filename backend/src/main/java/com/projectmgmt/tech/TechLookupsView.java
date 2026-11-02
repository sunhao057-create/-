package com.projectmgmt.tech;

import java.util.List;

public record TechLookupsView(
        List<FieldOption> fields,
        List<String> groups) {

    public record FieldOption(String key, String name, String type, List<String> options) {
    }
}
