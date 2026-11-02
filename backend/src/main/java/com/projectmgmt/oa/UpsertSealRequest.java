package com.projectmgmt.oa;

import java.util.List;

public record UpsertSealRequest(
        String belongCompany,
        List<String> sealTypes,
        String copyCount
) {
}
