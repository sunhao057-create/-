package com.projectmgmt.oa;

import java.util.List;

public record UpsertQualifyRequest(
        String lendDate,
        String returnDate,
        List<String> companies,
        List<String> items,
        Long companyAuditorId,
        Long financeAuditorId,
        Long contractAuditorId,
        String companyAuditorNote,
        String financeAuditorNote,
        String contractAuditorNote,
        String contractDesc,
        String otherCertNote
) {
}
