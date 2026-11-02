package com.projectmgmt.payment;

import com.projectmgmt.finance.BankTxnDirection;
import com.projectmgmt.finance.BankTxnSource;
import com.projectmgmt.finance.LedgerService;
import com.projectmgmt.role.FormModule;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentBudgetService {

    private final PaymentRepository paymentRepository;
    private final LedgerService ledgerService;

    @Transactional
    public void onWorkflowEnded(String moduleCode, Long bizId) {
        FormModule module = FormModule.fromNameOrNull(moduleCode);
        if (module == null) {
            return;
        }
        if (module != FormModule.PAYMENT || bizId == null) {
            return;
        }
        Payment payment = paymentRepository.findById(bizId).orElse(null);
        if (payment == null) {
            return;
        }
        if (payment.getProject() != null) {
            payment.setBudgetDeducted(true);
        }
        postLedger(payment);
    }

    @Transactional
    public void restoreIfDeducted(Payment payment) {
        if (payment == null) {
            return;
        }
        payment.setBudgetDeducted(false);
        ledgerService.reverse(BankTxnSource.PAYMENT, payment.getId());
    }

    public BigDecimal paidTotal(Long projectId) {
        if (projectId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = paymentRepository.paidTotal(projectId);
        return total == null ? BigDecimal.ZERO : total;
    }

    private void postLedger(Payment payment) {
        if (payment.getAccount() == null) {
            throw com.projectmgmt.common.ApiException.badRequest("请选择付款账户");
        }
        ledgerService.post(
                payment.getAccount(),
                payment.getExpectedPayDate(),
                BankTxnDirection.OUT,
                payment.getAmount(),
                "付款申请 " + payment.getCode() + " " + payment.getReason(),
                BankTxnSource.PAYMENT,
                payment.getId(),
                payment.getProject(),
                payment.getCreatedBy()
        );
    }
}
