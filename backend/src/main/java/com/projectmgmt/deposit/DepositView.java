package com.projectmgmt.deposit;

import com.projectmgmt.project.AttachmentView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DepositView(
        Long id,
        String code,
        String kind,
        String kindLabel,
        Long projectId,
        String projectName,
        String projectCode,
        String customerName,
        String customerShortName,
        LocalDateTime projectStartedAt,
        String belongCompany,
        String projectCategory,
        String managerName,
        String collaboratorName,
        String projectRemark,
        Long applyId,
        String applyCode,
        Long accountId,
        String accountName,
        String accountCompany,
        String bankName,
        String accountNo,
        BigDecimal amount,
        LocalDate bizDate,
        String remark,
        boolean ledgerPosted,
        boolean returned,
        BigDecimal remainingAmount,
        String workflowStatus,
        String workflowStatusLabel,
        List<AttachmentView> attachments,
        LocalDateTime createdAt
) {
}
