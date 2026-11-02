package com.projectmgmt.finance;

import com.projectmgmt.project.AttachmentView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ExpenseView(
        Long id,
        String code,
        Long projectId,
        String projectName,
        String projectCode,
        Long accountId,
        String accountName,
        BigDecimal amount,
        String reason,
        String category,
        String categoryLabel,
        LocalDate expenseDate,
        String payeeName,
        String bankName,
        String accountNo,
        String branch,
        boolean budgetDeducted,
        BigDecimal availableAmount,
        String workflowStatus,
        String workflowStatusLabel,
        List<AttachmentView> attachments,
        LocalDateTime createdAt
) {
    public static ExpenseView from(
            Expense expense,
            BigDecimal availableAmount,
            String workflowStatus,
            String workflowStatusLabel
    ) {
        return new ExpenseView(
                expense.getId(),
                expense.getCode(),
                expense.getProject() == null ? null : expense.getProject().getId(),
                expense.getProject() == null ? null : expense.getProject().getName(),
                expense.getProject() == null ? null : expense.getProject().getCode(),
                expense.getAccount().getId(),
                expense.getAccount().getName(),
                expense.getAmount(),
                expense.getReason(),
                expense.getCategory().name(),
                expense.getCategory().label(),
                expense.getExpenseDate(),
                expense.getPayeeName(),
                expense.getBankName(),
                expense.getAccountNo(),
                expense.getBranch(),
                expense.isBudgetDeducted(),
                availableAmount,
                workflowStatus,
                workflowStatusLabel,
                expense.getFiles().stream()
                        .map(file -> new AttachmentView(
                                file.getId(),
                                file.getOriginalName(),
                                file.getUrl(),
                                file.getSize(),
                                file.getContentType()
                        ))
                        .toList(),
                expense.getCreatedAt()
        );
    }
}
