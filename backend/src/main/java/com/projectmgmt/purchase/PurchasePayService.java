package com.projectmgmt.purchase;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.BizParse;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.finance.BankAccount;
import com.projectmgmt.finance.BankAccountRepository;
import com.projectmgmt.role.DataScopeQuery;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.workflow.WorkflowInstance;
import com.projectmgmt.workflow.WorkflowInstanceRepository;
import com.projectmgmt.workflow.WorkflowService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
public class PurchasePayService {

    private final PurchasePayRepository payRepository;
    private final PurchaseService purchaseService;
    private final BankAccountRepository accountRepository;
    private final WorkflowService workflowService;
    private final WorkflowInstanceRepository instanceRepository;
    private final PurchaseSettleService settleService;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public PageResult<PurchasePayView> list(String keyword, int page, int size) {
        Specification<PurchasePay> spec = (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            if (!StringUtils.hasText(keyword)) {
                return cb.conjunction();
            }
            String like = "%" + keyword.trim().toLowerCase() + "%";
            Join<PurchasePay, Purchase> purchase = root.join("purchase", JoinType.INNER);
            List<Predicate> ors = new ArrayList<>();
            ors.add(cb.like(cb.lower(root.get("code")), like));
            ors.add(cb.like(cb.lower(root.get("reason")), like));
            ors.add(cb.like(cb.lower(purchase.get("code")), like));
            ors.add(cb.like(cb.lower(purchase.get("itemName")), like));
            return cb.or(ors.toArray(Predicate[]::new));
        };
        spec = spec.and(DataScopeQuery.createdBy(permissionService.ownFilterUserId(FormModule.PURCHASE_PAY)));
        Page<PurchasePay> result = payRepository.findAll(
                spec,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id"))
        );
        return new PageResult<>(result.map(this::toView).getContent(), result.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public PurchasePayView get(Long id) {
        PurchasePay pay = require(id);
        permissionService.requireOwnRecord(FormModule.PURCHASE_PAY, pay.getCreatedBy());
        return toView(pay);
    }

    @Transactional
    public PurchasePayView create(UpsertPurchasePayRequest request, SysUser creator) {
        if (!workflowService.hasPublished(FormModule.PURCHASE_PAY)) {
            throw ApiException.badRequest("请先在流程引擎发布采购付款流程");
        }
        PurchasePay pay = new PurchasePay();
        pay.setCode(nextCode());
        pay.setCreatedBy(creator);
        apply(pay, request, Map.of(), false);
        assertWithinPurchase(pay.getPurchase(), pay.getAmount(), null);
        PurchasePay saved = payRepository.save(pay);
        workflowService.start(FormModule.PURCHASE_PAY, saved.getId());
        return toView(saved);
    }

    @Transactional
    public PurchasePayView update(Long id, UpsertPurchasePayRequest request) {
        PurchasePay pay = require(id);
        permissionService.requireOwnRecord(FormModule.PURCHASE_PAY, pay.getCreatedBy());
        Map<String, String> modes = workflowService.writeModes(FormModule.PURCHASE_PAY, id);
        apply(pay, request, modes, false);
        assertWithinPurchase(pay.getPurchase(), pay.getAmount(), pay.getId());
        return toView(pay);
    }

    @Transactional
    public void delete(Long id) {
        PurchasePay pay = require(id);
        permissionService.requireOwnRecord(FormModule.PURCHASE_PAY, pay.getCreatedBy());
        settleService.restorePay(pay);
        workflowService.deleteForBiz(FormModule.PURCHASE_PAY, id);
        payRepository.delete(pay);
    }

    private void apply(PurchasePay pay, UpsertPurchasePayRequest request, Map<String, String> modes, boolean posted) {
        if (writable(modes, "purchaseId")) {
            pay.setPurchase(purchaseService.require(request == null ? null : request.purchaseId()));
        } else if (pay.getPurchase() == null) {
            pay.setPurchase(purchaseService.require(request == null ? null : request.purchaseId()));
        }
        if (writable(modes, "accountId") || pay.getAccount() == null) {
            pay.setAccount(requireAccount(request == null ? null : request.accountId()));
        }
        if (writable(modes, "amount")) {
            pay.setAmount(BizParse.amount(request == null ? null : request.amount()));
        } else if (pay.getAmount() == null) {
            pay.setAmount(BizParse.amount(request == null ? null : request.amount()));
        }
        if (writable(modes, "reason") || pay.getReason() == null) {
            pay.setReason(BizParse.required(request == null ? null : request.reason(), "请填写付款事由"));
        }
        if (writable(modes, "payDate") || pay.getPayDate() == null) {
            pay.setPayDate(BizParse.date(request == null ? null : request.payDate(), "请选择付款日期"));
        }
    }

    private void assertWithinPurchase(Purchase purchase, BigDecimal amount, Long excludeId) {
        BigDecimal occupied = BizParse.nz(payRepository.paidOrPending(purchase.getId(), excludeId));
        BigDecimal room = BizParse.nz(purchase.getAmount()).subtract(occupied);
        if (amount.compareTo(room) > 0) {
            throw ApiException.badRequest("付款金额超出该采购申请未付余额（剩余 "
                    + room.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                    + "）");
        }
    }

    private BankAccount requireAccount(Long accountId) {
        if (accountId == null) {
            throw ApiException.badRequest("请选择付款账户");
        }
        BankAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.badRequest("银行账户不存在"));
        if (!account.isEnabled()) {
            throw ApiException.badRequest("该银行账户已停用");
        }
        return account;
    }

    private PurchasePay require(Long id) {
        return payRepository.findById(id).orElseThrow(() -> ApiException.notFound("采购付款不存在"));
    }

    private PurchasePayView toView(PurchasePay pay) {
        Purchase purchase = pay.getPurchase();
        BigDecimal occupied = BizParse.nz(payRepository.paidOrPending(purchase.getId(), null));
        BigDecimal unpaid = BizParse.nz(purchase.getAmount()).subtract(occupied);
        if (unpaid.compareTo(BigDecimal.ZERO) < 0) {
            unpaid = BigDecimal.ZERO;
        }
        WorkflowInstance instance = instanceRepository
                .findByFormModuleAndBizId(FormModule.PURCHASE_PAY, pay.getId())
                .orElse(null);
        String status = instance == null ? null : instance.getStatus();
        return new PurchasePayView(
                pay.getId(),
                pay.getCode(),
                purchase.getId(),
                purchase.getCode(),
                purchase.getProject() == null ? null : purchase.getProject().getId(),
                purchase.getProject() == null ? null : purchase.getProject().getName(),
                pay.getAccount().getId(),
                pay.getAccount().getName(),
                pay.getAmount(),
                pay.getReason(),
                pay.getPayDate(),
                pay.isLedgerPosted(),
                unpaid,
                status,
                workflowService.statusLabel(status),
                pay.getCreatedAt()
        );
    }

    private String nextCode() {
        String prefix = "CF" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 1; i < 1000; i++) {
            String code = prefix + String.format("%03d", i);
            if (!payRepository.existsByCode(code)) {
                return code;
            }
        }
        throw ApiException.badRequest("当日采购付款过多，请稍后重试");
    }

    private static boolean writable(Map<String, String> modes, String field) {
        if (modes == null || modes.isEmpty()) {
            return true;
        }
        return WorkflowService.EDITABLE.equals(modes.getOrDefault(field, WorkflowService.EDITABLE));
    }
}
