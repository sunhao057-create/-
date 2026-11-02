package com.projectmgmt.finance;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.LinkGuard;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.contract.ContractCollectionRepository;
import com.projectmgmt.payment.PaymentRepository;
import com.projectmgmt.project.Project;
import com.projectmgmt.project.ProjectRepository;
import com.projectmgmt.purchase.PurchasePayRepository;
import com.projectmgmt.role.DataScopeQuery;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.security.CurrentUser;
import com.projectmgmt.user.SysUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository accountRepository;
    private final BankTxnRepository txnRepository;
    private final ExpenseRepository expenseRepository;
    private final PurchasePayRepository purchasePayRepository;
    private final PaymentRepository paymentRepository;
    private final ContractCollectionRepository collectionRepository;
    private final ProjectRepository projectRepository;
    private final LedgerService ledgerService;
    private final PermissionService permissionService;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<BankAccountView> list(String keyword) {
        Specification<BankAccount> spec = DataScopeQuery.createdBy(permissionService.ownFilterUserId(FormModule.BANK_ACCOUNT));
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("bankName")), like),
                    cb.like(cb.lower(root.get("accountName")), like),
                    cb.like(cb.lower(root.get("accountNo")), like)
            ));
        }
        return accountRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public BankAccountView get(Long id) {
        BankAccount account = require(id);
        permissionService.requireOwnRecord(FormModule.BANK_ACCOUNT, account.getCreatedBy());
        return toView(account);
    }

    @Transactional
    public BankAccountView create(UpsertBankAccountRequest request, SysUser actor) {
        BankAccount account = new BankAccount();
        account.setCreatedBy(actor);
        apply(account, request, true);
        return toView(accountRepository.save(account));
    }

    @Transactional
    public BankAccountView update(Long id, UpsertBankAccountRequest request) {
        BankAccount account = require(id);
        permissionService.requireOwnRecord(FormModule.BANK_ACCOUNT, account.getCreatedBy());
        apply(account, request, false);
        return toView(account);
    }

    @Transactional
    public void delete(Long id) {
        BankAccount account = require(id);
        permissionService.requireOwnRecord(FormModule.BANK_ACCOUNT, account.getCreatedBy());
        if (txnRepository.existsByAccount_Id(id)
                || expenseRepository.existsByAccount_Id(id)
                || purchasePayRepository.existsByAccount_Id(id)
                || paymentRepository.existsByAccount_Id(id)
                || collectionRepository.existsByAccount_Id(id)) {
            throw ApiException.badRequest("该账户已有流水或收付款记录，不能删除");
        }
        accountRepository.delete(account);
    }

    @Transactional(readOnly = true)
    public PageResult<BankTxnView> txns(Long accountId, int page, int size) {
        BankAccount account = require(accountId);
        permissionService.requireOwnRecord(FormModule.BANK_ACCOUNT, account.getCreatedBy());
        Specification<BankTxn> spec = (root, query, cb) -> cb.equal(root.get("account").get("id"), accountId);
        Page<BankTxn> result = txnRepository.findAll(
                spec,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "txnDate").and(Sort.by(Sort.Direction.DESC, "id")))
        );
        return new PageResult<>(result.map(BankTxnView::from).getContent(), result.getTotalElements(), page, size);
    }

    @Transactional
    public BankTxnView addTxn(Long accountId, UpsertBankTxnRequest request) {
        BankAccount account = require(accountId);
        permissionService.requireOwnRecord(FormModule.BANK_ACCOUNT, account.getCreatedBy());
        if (!account.isEnabled()) {
            throw ApiException.badRequest("该银行账户已停用");
        }
        Project project = null;
        if (request.projectId() != null) {
            project = projectRepository.findById(request.projectId())
                    .orElseThrow(() -> ApiException.badRequest("项目不存在"));
            LinkGuard.assertProjectLinkable(project);
        }
        BankTxn saved = ledgerService.post(
                account,
                parseDate(request.txnDate()),
                BankTxnDirection.from(request.direction()),
                parseAmount(request.amount()),
                request.summary(),
                BankTxnSource.MANUAL,
                null,
                project,
                currentUser.entity()
        );
        return BankTxnView.from(saved);
    }

    @Transactional
    public void deleteTxn(Long accountId, Long txnId) {
        BankAccount account = require(accountId);
        permissionService.requireOwnRecord(FormModule.BANK_ACCOUNT, account.getCreatedBy());
        BankTxn txn = txnRepository.findById(txnId).orElseThrow(() -> ApiException.notFound("流水不存在"));
        if (!accountId.equals(txn.getAccount().getId())) {
            throw ApiException.badRequest("流水不属于该账户");
        }
        if (txn.getSourceType() != BankTxnSource.MANUAL) {
            throw ApiException.badRequest("系统生成的流水不能手工删除");
        }
        txnRepository.delete(txn);
    }

    private void apply(BankAccount account, UpsertBankAccountRequest request, boolean creating) {
        String name = trimRequired(request == null ? null : request.name(), "请填写账户名称");
        String bankName = trimRequired(request == null ? null : request.bankName(), "请填写开户银行");
        String accountName = trimRequired(request == null ? null : request.accountName(), "请填写户名");
        String accountNo = trimRequired(request == null ? null : request.accountNo(), "请填写银行账号");
        if (creating ? accountRepository.existsByAccountNo(accountNo)
                : accountRepository.existsByAccountNoAndIdNot(accountNo, account.getId())) {
            throw ApiException.badRequest("银行账号已存在");
        }
        account.setName(name);
        account.setBankName(bankName);
        account.setAccountName(accountName);
        account.setAccountNo(accountNo);
        account.setBranch(blankToNull(request == null ? null : request.branch()));
        account.setOpeningBalance(parseOpening(request == null ? null : request.openingBalance()));
        account.setRemark(blankToNull(request == null ? null : request.remark()));
        account.setEnabled(request == null || request.enabled() == null || Boolean.TRUE.equals(request.enabled()));
    }

    private BankAccount require(Long id) {
        return accountRepository.findById(id).orElseThrow(() -> ApiException.notFound("银行账户不存在"));
    }

    private BankAccountView toView(BankAccount account) {
        BigDecimal income = txnRepository.sumByAccountAndDirection(account.getId(), BankTxnDirection.IN);
        BigDecimal expense = txnRepository.sumByAccountAndDirection(account.getId(), BankTxnDirection.OUT);
        return BankAccountView.from(account, income, expense);
    }

    private static String trimRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw ApiException.badRequest(message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static BigDecimal parseOpening(BigDecimal value) {
        BigDecimal next = value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest("期初余额不能为负数");
        }
        return next;
    }

    private static BigDecimal parseAmount(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw ApiException.badRequest("请填写金额");
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim()).setScale(2, RoundingMode.HALF_UP);
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.badRequest("金额必须大于 0");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("金额格式不正确");
        }
    }

    private static LocalDate parseDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw ApiException.badRequest("请选择流水日期");
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest("流水日期格式不正确");
        }
    }
}
