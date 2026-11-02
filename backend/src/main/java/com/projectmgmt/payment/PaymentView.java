package com.projectmgmt.payment;

import com.projectmgmt.project.AttachmentView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PaymentView(
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
        Long accountId,
        String accountName,
        Long bankAccountId,
        String payeeName,
        String bankName,
        String accountNo,
        String branch,
        String payMethod,
        String payMethodLabel,
        LocalDate expectedPayDate,
        boolean budgetDeducted,
        BigDecimal availableAmount,
        BigDecimal paidTotal,
        String workflowStatus,
        String workflowStatusLabel,
        boolean isPublic,
        String submitSource,
        List<AttachmentView> attachments,
        LocalDateTime createdAt
) {
    public static PaymentView from(
            Payment payment,
            BigDecimal availableAmount,
            BigDecimal paidTotal,
            String workflowStatus,
            String workflowStatusLabel
    ) {
        return new PaymentView(
                payment.getId(),
                payment.getCode(),
                payment.getProject() == null ? null : payment.getProject().getId(),
                payment.getProject() == null ? null : payment.getProject().getName(),
                payment.getProject() == null ? null : payment.getProject().getCode(),
                payment.getAmount(),
                payment.getReason(),
                payment.getSupplier().getId(),
                payment.getSupplier().getShortName(),
                payment.getSupplier().getCompanyName(),
                payment.getAccount() == null ? null : payment.getAccount().getId(),
                payment.getAccount() == null ? null : payment.getAccount().getName(),
                payment.getBankAccountId(),
                payment.getPayeeName(),
                payment.getBankName(),
                payment.getAccountNo(),
                payment.getBranch(),
                payment.getPayMethod().name(),
                payment.getPayMethod().label(),
                payment.getExpectedPayDate(),
                payment.isBudgetDeducted(),
                availableAmount,
                paidTotal,
                workflowStatus,
                workflowStatusLabel,
                payment.isPublic(),
                payment.getSubmitSource() == null ? "ADMIN" : payment.getSubmitSource(),
                payment.getFiles().stream()
                        .map(file -> new AttachmentView(
                                file.getId(),
                                file.getOriginalName(),
                                file.getUrl(),
                                file.getSize(),
                                file.getContentType()
                        ))
                        .toList(),
                payment.getCreatedAt()
        );
    }
}
