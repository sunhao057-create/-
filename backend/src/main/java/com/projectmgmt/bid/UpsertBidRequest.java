package com.projectmgmt.bid;

import java.math.BigDecimal;
import java.util.List;

public record UpsertBidRequest(
        Long customerId,
        Long salesLeadId,
        Long projectId,
        String opportunityName,
        String bidAgent,
        String tenderName,
        String tenderCode,
        String downloadDeadline,
        String bidTime,
        String description,
        String announcementUrl,
        Boolean signedUp,
        String result,
        BigDecimal winAmount,
        String winUnit,
        String winUnitName,
        String round2Name,
        String round2DownloadDeadline,
        String round2BidTime,
        String round2AnnouncementUrl,
        String round2Result,
        BigDecimal round2WinAmount,
        String round2WinUnit,
        String round2WinUnitName,
        String finalContractName,
        List<BidSignupLine> signups
) {
    public record BidSignupLine(String companyName, Long bidderId, String registrantName) {
    }
}
