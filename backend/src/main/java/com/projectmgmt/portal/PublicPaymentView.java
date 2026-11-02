package com.projectmgmt.portal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PublicPaymentView(
        Long id,
        String code,
        Long projectId,
        String projectName,
        String projectCode,
        BigDecimal amount,
        String reason,
        Long supplierId,
        String supplierShortName,
        String supplierCompanyName,
        String payeeName,
        String bankName,
        String accountNo,
        String branch,
        String payMethod,
        String payMethodLabel,
        LocalDate expectedPayDate,
        Long accountId,
        String accountName,
        List<PublicAttachmentView> attachments
) {}
