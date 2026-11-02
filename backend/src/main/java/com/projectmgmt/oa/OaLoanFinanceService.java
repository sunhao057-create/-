package com.projectmgmt.oa;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.LinkGuard;
import com.projectmgmt.customer.Customer;
import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.finance.BankAccount;
import com.projectmgmt.finance.BankAccountRepository;
import com.projectmgmt.finance.BankTxnDirection;
import com.projectmgmt.finance.BankTxnSource;
import com.projectmgmt.finance.LedgerService;
import com.projectmgmt.project.Project;
import com.projectmgmt.project.ProjectRepository;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OaLoanFinanceService {

    private final OaRequestRepository requestRepository;
    private final ProjectRepository projectRepository;
    private final CustomerRepository customerRepository;
    private final BankAccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final PermissionService permissionService;

    @Transactional
    public void settle(OaRequest item) {
        if (item == null || item.isApplied()) {
            return;
        }
        OaKind kind = item.getKind();
        if (kind != OaKind.LOAN && kind != OaKind.LOAN_PAY && kind != OaKind.LOAN_REPAY) {
            return;
        }
        OaLoanMoney extra = extra(item);
        BankAccount account = requireAccount(extra == null ? null : extra.accountId());
        BigDecimal amount = money(kind == OaKind.LOAN_REPAY ? extra.repayAmount() : extra.payAmount());
        Project project = linkedProject(item);
        BankTxnDirection direction = kind == OaKind.LOAN_REPAY ? BankTxnDirection.IN : BankTxnDirection.OUT;
        BankTxnSource source = switch (kind) {
            case LOAN -> BankTxnSource.OA_LOAN;
            case LOAN_PAY -> BankTxnSource.OA_LOAN_PAY;
            default -> BankTxnSource.OA_LOAN_REPAY;
        };
        ledgerService.post(
                account,
                item.getExpectedDate(),
                direction,
                amount,
                kind.label() + " " + item.getCode() + " " + item.getReason(),
                source,
                item.getId(),
                project,
                item.getCreatedBy()
        );
    }

    @Transactional
    public void restoreIfPosted(OaRequest item) {
        if (item == null || !item.isApplied()) {
            return;
        }
        OaKind kind = item.getKind();
        if (kind != OaKind.LOAN && kind != OaKind.LOAN_PAY && kind != OaKind.LOAN_REPAY) {
            return;
        }
        BankTxnSource source = switch (kind) {
            case LOAN -> BankTxnSource.OA_LOAN;
            case LOAN_PAY -> BankTxnSource.OA_LOAN_PAY;
            default -> BankTxnSource.OA_LOAN_REPAY;
        };
        ledgerService.reverse(source, item.getId());
    }

    public void assertCanCreate(OaRequest item, OaKind kind, OaLoanMoney extra, Long excludeId) {
        if (kind == OaKind.LOAN) {
            BigDecimal amount = money(extra.amount());
            BigDecimal pay = money(extra.payAmount());
            if (pay.compareTo(amount) > 0) {
                throw ApiException.badRequest("本次付款金额不能大于借款金额");
            }
            return;
        }
        OaRequest loan = requireLoan(extra == null ? null : extra.loanRequestId());
        if (!loan.isApplied()) {
            throw ApiException.badRequest("请待项目借款审批通过后再办理");
        }
        OaLoanView totals = summarize(loan.getId(), excludeId);
        if (kind == OaKind.LOAN_PAY) {
            BigDecimal pay = money(extra.payAmount());
            if (pay.compareTo(nz(totals.remainingPay())) > 0) {
                throw ApiException.badRequest("本次付款超过剩余可付金额");
            }
        } else {
            BigDecimal repay = money(extra.repayAmount());
            if (repay.compareTo(nz(totals.remainingRepay())) > 0) {
                throw ApiException.badRequest("本次还款超过剩余应还金额");
            }
        }
    }

    public OaLoanView summarize(Long loanId) {
        return summarize(loanId, null);
    }

    public OaLoanView toView(OaRequest item) {
        if (item.getKind() == OaKind.LOAN) {
            return summarize(item.getId(), null);
        }
        OaLoanMoney extra = extra(item);
        OaRequest loan = extra == null || extra.loanRequestId() == null
                ? null
                : requestRepository.findById(extra.loanRequestId()).orElse(null);
        OaLoanView totals = loan == null ? emptyView(extra) : summarize(loan.getId(), item.getId());
        BankAccount account = extra == null || extra.accountId() == null
                ? null
                : accountRepository.findById(extra.accountId()).orElse(null);
        return new OaLoanView(
                totals.linkProject(),
                totals.projectId(),
                totals.projectName(),
                totals.projectCode(),
                totals.customerId(),
                totals.customerName(),
                totals.belongCompany(),
                totals.amount(),
                item.getKind() == OaKind.LOAN_PAY ? extra.payAmount() : totals.payAmount(),
                account == null ? null : account.getId(),
                account == null ? null : account.getName(),
                account == null ? null : account.getAccountNo(),
                account == null ? null : account.getBankName(),
                loan == null ? extra.loanRequestId() : loan.getId(),
                loan == null ? null : loan.getCode(),
                item.getKind() == OaKind.LOAN_REPAY ? extra.repayAmount() : totals.repayAmount(),
                totals.paidTotal(),
                totals.pendingPayTotal(),
                totals.remainingPay(),
                totals.repayTotal(),
                totals.pendingRepayTotal(),
                totals.remainingRepay(),
                totals.repayStatus(),
                totals.loanStatus()
        );
    }

    private OaLoanView summarize(Long loanId, Long excludeId) {
        OaRequest loan = requireLoan(loanId);
        OaLoanMoney extra = extra(loan);
        BigDecimal amount = money(extra.amount());
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal pendingPay = BigDecimal.ZERO;
        if (loan.isApplied() && (excludeId == null || !excludeId.equals(loan.getId()))) {
            paid = paid.add(money(extra.payAmount()));
        } else if (!loan.isApplied() && (excludeId == null || !excludeId.equals(loan.getId()))) {
            pendingPay = pendingPay.add(money(extra.payAmount()));
        }
        for (OaRequest pay : requestRepository.findByKindAndLinkRequestId(OaKind.LOAN_PAY, loanId)) {
            if (excludeId != null && excludeId.equals(pay.getId())) {
                continue;
            }
            BigDecimal value = money(extra(pay).payAmount());
            if (pay.isApplied()) {
                paid = paid.add(value);
            } else {
                pendingPay = pendingPay.add(value);
            }
        }
        BigDecimal repaid = BigDecimal.ZERO;
        BigDecimal pendingRepay = BigDecimal.ZERO;
        for (OaRequest repay : requestRepository.findByKindAndLinkRequestId(OaKind.LOAN_REPAY, loanId)) {
            if (excludeId != null && excludeId.equals(repay.getId())) {
                continue;
            }
            BigDecimal value = money(extra(repay).repayAmount());
            if (repay.isApplied()) {
                repaid = repaid.add(value);
            } else {
                pendingRepay = pendingRepay.add(value);
            }
        }
        BigDecimal remainingPay = amount.subtract(paid).subtract(pendingPay);
        if (remainingPay.compareTo(BigDecimal.ZERO) < 0) {
            remainingPay = BigDecimal.ZERO;
        }
        BigDecimal remainingRepay = paid.subtract(repaid).subtract(pendingRepay);
        if (remainingRepay.compareTo(BigDecimal.ZERO) < 0) {
            remainingRepay = BigDecimal.ZERO;
        }
        String repayStatus = repaid.compareTo(BigDecimal.ZERO) <= 0
                ? "未还款"
                : repaid.compareTo(paid) >= 0 && paid.compareTo(BigDecimal.ZERO) > 0 ? "已还清" : "部分还款";
        Project project = projectOf(extra);
        Customer customer = extra.customerId() == null
                ? (project == null ? null : project.getCustomer())
                : customerRepository.findById(extra.customerId()).orElse(null);
        BankAccount account = extra.accountId() == null ? null : accountRepository.findById(extra.accountId()).orElse(null);
        if (project != null) {
            project.getCustomer();
        }
        return new OaLoanView(
                Boolean.TRUE.equals(extra.linkProject()),
                project == null ? extra.projectId() : project.getId(),
                project == null ? null : project.getName(),
                project == null ? null : project.getCode(),
                customer == null ? extra.customerId() : customer.getId(),
                customer == null ? null : customer.getShortName(),
                extraBelong(loan),
                amount,
                extra.payAmount(),
                account == null ? extra.accountId() : account.getId(),
                account == null ? null : account.getName(),
                account == null ? null : account.getAccountNo(),
                account == null ? null : account.getBankName(),
                loan.getId(),
                loan.getCode(),
                extra.repayAmount(),
                paid,
                pendingPay,
                remainingPay,
                repaid,
                pendingRepay,
                remainingRepay,
                repayStatus,
                "正常"
        );
    }

    public OaRequest requireLoan(Long loanRequestId) {
        if (loanRequestId == null) {
            throw ApiException.badRequest("请选择项目借款单");
        }
        OaRequest loan = requestRepository.findById(loanRequestId)
                .orElseThrow(() -> ApiException.badRequest("项目借款单不存在"));
        if (loan.getKind() != OaKind.LOAN) {
            throw ApiException.badRequest("请选择项目借款单");
        }
        return loan;
    }

    public OaLoanMoney extra(OaRequest item) {
        OaFormExtra extra = parse(item);
        return extra.loan() == null
                ? new OaLoanMoney(false, null, null, null, null, null, item.getLinkRequestId(), null)
                : extra.loan();
    }

    public BankAccount requireAccount(Long accountId) {
        if (accountId == null) {
            throw ApiException.badRequest("请选择银行账户");
        }
        BankAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.badRequest("银行账户不存在"));
        if (!account.isEnabled()) {
            throw ApiException.badRequest("该银行账户已停用");
        }
        return account;
    }

    public Project optionalProject(Boolean linkProject, Long projectId, Long previousId) {
        if (!Boolean.TRUE.equals(linkProject) || projectId == null) {
            return null;
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.badRequest("项目不存在"));
        LinkGuard.assertNewProjectLink(project, previousId);
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.projectmgmt.security.AuthUser) {
            permissionService.requireOwnRecord(FormModule.PROJECT, project.getCreatedBy());
        }
        return project;
    }

    public Customer optionalCustomer(Long customerId, Project project) {
        if (project != null && project.getCustomer() != null) {
            return project.getCustomer();
        }
        if (customerId == null) {
            return null;
        }
        return customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.badRequest("客户不存在"));
    }

    private Project linkedProject(OaRequest item) {
        if (item.getKind() == OaKind.LOAN) {
            return projectOf(extra(item));
        }
        OaRequest loan = requireLoan(item.getLinkRequestId() == null ? extra(item).loanRequestId() : item.getLinkRequestId());
        return projectOf(extra(loan));
    }

    private Project projectOf(OaLoanMoney extra) {
        if (extra == null || !Boolean.TRUE.equals(extra.linkProject()) || extra.projectId() == null) {
            return null;
        }
        return projectRepository.findById(extra.projectId()).orElse(null);
    }

    private String extraBelong(OaRequest loan) {
        OaFormExtra extra = parse(loan);
        return extra.belongCompany();
    }

    private OaFormExtra parse(OaRequest item) {
        if (item == null || !StringUtils.hasText(item.getExtraJson())) {
            return OaFormExtra.empty();
        }
        try {
            OaFormExtra extra = tools.jackson.databind.json.JsonMapper.shared().readValue(item.getExtraJson(), OaFormExtra.class);
            return extra == null ? OaFormExtra.empty() : extra;
        } catch (Exception ex) {
            return OaFormExtra.empty();
        }
    }

    private OaLoanView emptyView(OaLoanMoney extra) {
        return new OaLoanView(
                extra == null ? false : extra.linkProject(),
                extra == null ? null : extra.projectId(),
                null,
                null,
                extra == null ? null : extra.customerId(),
                null,
                null,
                extra == null ? null : extra.amount(),
                extra == null ? null : extra.payAmount(),
                extra == null ? null : extra.accountId(),
                null,
                null,
                null,
                extra == null ? null : extra.loanRequestId(),
                null,
                extra == null ? null : extra.repayAmount(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "未还款",
                "正常"
        );
    }

    public static BigDecimal money(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("金额必须大于 0");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
