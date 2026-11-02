package com.projectmgmt.deposit;

import com.projectmgmt.finance.BankTxnDirection;
import com.projectmgmt.finance.BankTxnSource;
import com.projectmgmt.finance.LedgerService;
import com.projectmgmt.role.FormModule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DepositSettleService {

    private final DepositRepository depositRepository;
    private final LedgerService ledgerService;

    @Transactional
    public void onWorkflowEnded(String moduleCode, Long bizId) {
        FormModule module = FormModule.fromNameOrNull(moduleCode);
        if (module == null) {
            return;
        }
        DepositKind kind = DepositKind.fromModule(module);
        if (kind == null || bizId == null) {
            return;
        }
        Deposit item = depositRepository.findById(bizId).orElse(null);
        if (item == null || item.isLedgerPosted()) {
            return;
        }
        item.setLedgerPosted(true);
        BankTxnDirection direction = kind == DepositKind.APPLY ? BankTxnDirection.OUT : BankTxnDirection.IN;
        BankTxnSource source = kind == DepositKind.APPLY ? BankTxnSource.DEPOSIT_APPLY : BankTxnSource.DEPOSIT_RETURN;
        ledgerService.post(
                item.getAccount(),
                item.getBizDate(),
                direction,
                item.getAmount(),
                kind.label() + " " + item.getCode(),
                source,
                item.getId(),
                item.getProject(),
                item.getCreatedBy()
        );
    }

    @Transactional
    public void restoreIfPosted(Deposit item) {
        if (item == null) {
            return;
        }
        item.setLedgerPosted(false);
        BankTxnSource source = item.getKind() == DepositKind.APPLY
                ? BankTxnSource.DEPOSIT_APPLY
                : BankTxnSource.DEPOSIT_RETURN;
        ledgerService.reverse(source, item.getId());
    }
}
