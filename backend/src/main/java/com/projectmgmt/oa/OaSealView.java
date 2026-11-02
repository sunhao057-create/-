package com.projectmgmt.oa;

import java.util.List;

public record OaSealView(
        String belongCompany,
        List<String> sealTypes,
        String copyCount
) {
}
