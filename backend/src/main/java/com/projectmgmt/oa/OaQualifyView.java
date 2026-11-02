package com.projectmgmt.oa;

import java.util.List;

public record OaQualifyView(
        String lendDate,
        String returnDate,
        List<String> companies,
        List<String> items,
        Long companyAuditorId,
        String companyAuditorName,
        Long financeAuditorId,
        String financeAuditorName,
        Long contractAuditorId,
        String contractAuditorName,
        String companyAuditorNote,
        String financeAuditorNote,
        String contractAuditorNote,
        String contractDesc,
        String otherCertNote
) {
}
