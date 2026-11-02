package com.projectmgmt.bid;

import java.time.LocalDateTime;
import java.util.List;

public record BidView(
        Long id,
        String code,
        Long customerId,
        String customerName,
        String customerShortName,
        Long salesLeadId,
        String salesLeadName,
        Long projectId,
        String projectName,
        String projectCode,
        String opportunityName,
        String bidAgent,
        String tenderName,
        String tenderCode,
        LocalDateTime downloadDeadline,
        LocalDateTime bidTime,
        String description,
        String announcementUrl,
        boolean signedUp,
        String result,
        String resultLabel,
        java.math.BigDecimal winAmount,
        String winUnit,
        String winUnitName,
        String round2Name,
        LocalDateTime round2DownloadDeadline,
        LocalDateTime round2BidTime,
        String round2AnnouncementUrl,
        String round2Result,
        String round2ResultLabel,
        java.math.BigDecimal round2WinAmount,
        String round2WinUnit,
        String round2WinUnitName,
        String finalContractName,
        List<BidSignupView> signups,
        List<BidFileView> screenshots,
        List<BidFileView> awardFiles,
        List<BidFileView> round2AwardFiles,
        String workflowStatus,
        String workflowStatusLabel,
        LocalDateTime createdAt
) {
    public record BidSignupView(Long id, String companyName, Long bidderId, String bidderName, String registrantName) {
    }

    public record BidFileView(Long id, String category, String originalName, String url, long size, String contentType) {
    }
}
