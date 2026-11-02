package com.projectmgmt.finance;

import com.projectmgmt.role.FormModule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExpenseBudgetService {

    private final ExpenseRepository expenseRepository;
    private final LedgerService ledgerService;

    @Transactional
    public void onWorkflowEnded(String moduleCode, Long bizId) {
        FormModule module = FormModule.fromNameOrNull(moduleCode);
        if (module == null) {
            return;
        }
        if (module != FormModule.EXPENSE || bizId == null) {
            return;
        }
        Expense expense = expenseRepository.findById(bizId).orElse(null);
        if (expense == null) {
            return;
        }
        if (expense.getProject() != null) {
            expense.setBudgetDeducted(true);
        }
        ledgerService.post(
                expense.getAccount(),
                expense.getExpenseDate(),
                BankTxnDirection.OUT,
                expense.getAmount(),
                "报销 " + expense.getCode() + " " + expense.getReason(),
                BankTxnSource.EXPENSE,
                expense.getId(),
                expense.getProject(),
                expense.getCreatedBy()
        );
    }

    @Transactional
    public void restoreIfDeducted(Expense expense) {
        if (expense == null) {
            return;
        }
        expense.setBudgetDeducted(false);
        ledgerService.reverse(BankTxnSource.EXPENSE, expense.getId());
    }
}
