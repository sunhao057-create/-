package com.projectmgmt.finance;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.project.Project;
import com.projectmgmt.user.SysUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final BankTxnRepository txnRepository;

    @Transactional
    public BankTxn post(
            BankAccount account,
            LocalDate txnDate,
            BankTxnDirection direction,
            BigDecimal amount,
            String summary,
            BankTxnSource sourceType,
            Long sourceId,
            Project project,
            SysUser actor
    ) {
        if (account == null) {
            throw ApiException.badRequest("请选择银行账户");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("金额必须大于 0");
        }
        if (sourceType != BankTxnSource.MANUAL && sourceId != null) {
            BankTxn existing = txnRepository.findBySourceTypeAndSourceId(sourceType, sourceId).orElse(null);
            if (existing != null) {
                return existing;
            }
        }
        BankTxn txn = new BankTxn();
        txn.setAccount(account);
        txn.setTxnDate(txnDate == null ? LocalDate.now() : txnDate);
        txn.setDirection(direction);
        txn.setAmount(amount);
        txn.setSummary(summary == null || summary.isBlank() ? direction.label() : summary.trim());
        txn.setSourceType(sourceType);
        txn.setSourceId(sourceId);
        txn.setProject(project);
        txn.setCreatedBy(actor);
        return txnRepository.save(txn);
    }

    @Transactional
    public BankTxn replace(
            BankAccount account,
            LocalDate txnDate,
            BankTxnDirection direction,
            BigDecimal amount,
            String summary,
            BankTxnSource sourceType,
            Long sourceId,
            Project project,
            SysUser actor
    ) {
        reverse(sourceType, sourceId);
        return post(account, txnDate, direction, amount, summary, sourceType, sourceId, project, actor);
    }

    @Transactional
    public void reverse(BankTxnSource sourceType, Long sourceId) {
        if (sourceType == null || sourceId == null) {
            return;
        }
        txnRepository.findBySourceTypeAndSourceId(sourceType, sourceId).ifPresent(txnRepository::delete);
    }
}
