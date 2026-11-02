package com.projectmgmt.payment;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.LinkGuard;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.finance.BankAccount;
import com.projectmgmt.finance.BankAccountRepository;
import com.projectmgmt.profile.FileStorageService;
import com.projectmgmt.project.Project;
import com.projectmgmt.project.ProjectRepository;
import com.projectmgmt.role.DataScopeQuery;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.supplier.Supplier;
import com.projectmgmt.supplier.SupplierBankAccount;
import com.projectmgmt.supplier.SupplierRepository;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.workflow.WorkflowInstance;
import com.projectmgmt.workflow.WorkflowInstanceRepository;
import com.projectmgmt.workflow.WorkflowService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ProjectRepository projectRepository;
    private final SupplierRepository supplierRepository;
    private final BankAccountRepository accountRepository;
    private final FileStorageService fileStorageService;
    private final WorkflowService workflowService;
    private final WorkflowInstanceRepository instanceRepository;
    private final PaymentBudgetService paymentBudgetService;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public PageResult<PaymentView> list(String keyword, int page, int size) {
        Specification<Payment> spec = (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            if (!StringUtils.hasText(keyword)) {
                return cb.conjunction();
            }
            String like = "%" + keyword.trim().toLowerCase() + "%";
            Join<Payment, Project> project = root.join("project", JoinType.LEFT);
            Join<Payment, Supplier> supplier = root.join("supplier", JoinType.INNER);
            return cb.or(
                    cb.like(cb.lower(root.get("code")), like),
                    cb.like(cb.lower(project.get("name")), like),
                    cb.like(cb.lower(project.get("code")), like),
                    cb.like(cb.lower(supplier.get("shortName")), like),
                    cb.like(cb.lower(supplier.get("companyName")), like)
            );
        };
        spec = spec.and(DataScopeQuery.createdBy(permissionService.ownFilterUserId(FormModule.PAYMENT)));
        Page<Payment> result = paymentRepository.findAll(
                spec,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id"))
        );
        return new PageResult<>(result.map(this::toView).getContent(), result.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public PaymentView get(Long id) {
        Payment payment = require(id);
        permissionService.requireOwnRecord(FormModule.PAYMENT, payment.getCreatedBy());
        payment.getFiles().size();
        if (payment.getAccount() != null) {
            payment.getAccount().getName();
        }
        return toView(payment);
    }

    @Transactional
    public PaymentView create(
            Long projectId,
            String amount,
            String reason,
            Long supplierId,
            Long accountId,
            Long bankAccountId,
            String payMethod,
            String expectedPayDate,
            SysUser creator,
            List<MultipartFile> files,
            Boolean isPublic
    ) {
        if (!workflowService.hasPublished(FormModule.PAYMENT)) {
            throw ApiException.badRequest("请先在流程引擎发布付款申请流程");
        }
        Payment payment = new Payment();
        payment.setCode(nextCode());
        payment.setCreatedBy(creator);
        payment.setSubmitSource("ADMIN");
        payment.setPublic(Boolean.TRUE.equals(isPublic));
        applyCore(payment, projectId, amount, reason, supplierId, accountId, bankAccountId, payMethod, expectedPayDate, Map.of(), false);
        addFiles(payment, files);
        Payment saved = paymentRepository.save(payment);
        workflowService.start(FormModule.PAYMENT, saved.getId());
        return toView(saved);
    }

    @Transactional
    public Payment createPublic(
            Long projectId,
            String amount,
            String reason,
            Long supplierId,
            Long accountId,
            Long bankAccountId,
            String payMethod,
            String expectedPayDate,
            List<MultipartFile> files
    ) {
        if (!workflowService.hasPublished(FormModule.PAYMENT)) {
            throw ApiException.badRequest("请先在流程引擎发布付款申请流程");
        }
        Project project = optionalPublicProject(projectId);
        Payment payment = new Payment();
        payment.setCode(nextCode());
        payment.setCreatedBy(null);
        payment.setSubmitSource("PUBLIC");
        payment.setPublic(false);
        payment.setProject(project);
        payment.setAmount(parseAmount(amount));
        if (!StringUtils.hasText(reason)) {
            throw ApiException.badRequest("请填写付款事由");
        }
        payment.setReason(reason.trim());
        requirePublicSupplier(supplierId);
        payment.setAccount(requireCompanyAccount(accountId));
        applyPayee(payment, supplierId, bankAccountId);
        payment.setPayMethod(PayMethod.from(payMethod));
        payment.setExpectedPayDate(parseDate(expectedPayDate));
        addFiles(payment, files);
        Payment saved = paymentRepository.save(payment);
        workflowService.start(FormModule.PAYMENT, saved.getId());
        return saved;
    }

    @Transactional
    public PaymentView update(
            Long id,
            Long projectId,
            String amount,
            String reason,
            Long supplierId,
            Long accountId,
            Long bankAccountId,
            String payMethod,
            String expectedPayDate,
            List<MultipartFile> files,
            Boolean isPublic
    ) {
        Payment payment = require(id);
        permissionService.requireOwnRecord(FormModule.PAYMENT, payment.getCreatedBy());
        Map<String, String> modes = workflowService.writeModes(FormModule.PAYMENT, id);
        applyCore(payment, projectId, amount, reason, supplierId, accountId, bankAccountId, payMethod, expectedPayDate, modes, payment.isBudgetDeducted());
        if (isPublic != null) {
            payment.setPublic(isPublic);
        }
        addFiles(payment, files);
        return toView(payment);
    }

    @Transactional
    public void delete(Long id) {
        Payment payment = require(id);
        permissionService.requireOwnRecord(FormModule.PAYMENT, payment.getCreatedBy());
        paymentBudgetService.restoreIfDeducted(payment);
        workflowService.deleteForBiz(FormModule.PAYMENT, id);
        paymentRepository.delete(payment);
    }

    private void applyCore(
            Payment payment,
            Long projectId,
            String amount,
            String reason,
            Long supplierId,
            Long accountId,
            Long bankAccountId,
            String payMethod,
            String expectedPayDate,
            Map<String, String> modes,
            boolean deducted
    ) {
        if (writable(modes, "projectId")) {
            payment.setProject(optionalProject(projectId, previousProjectId(payment)));
        } else if (payment.getId() == null) {
            payment.setProject(optionalProject(projectId, null));
        }
        if (writable(modes, "amount")) {
            payment.setAmount(parseAmount(amount));
        } else if (payment.getAmount() == null) {
            payment.setAmount(parseAmount(amount));
        }
        if (writable(modes, "reason")) {
            if (!StringUtils.hasText(reason)) {
                throw ApiException.badRequest("请填写付款事由");
            }
            payment.setReason(reason.trim());
        } else if (payment.getReason() == null) {
            if (!StringUtils.hasText(reason)) {
                throw ApiException.badRequest("请填写付款事由");
            }
            payment.setReason(reason.trim());
        }
        if (writable(modes, "accountId") || payment.getAccount() == null) {
            payment.setAccount(requireCompanyAccount(accountId));
        }
        boolean supplierWritable = writable(modes, "supplierId");
        boolean accountWritable = writable(modes, "bankAccountId");
        if (supplierWritable || accountWritable || payment.getSupplier() == null) {
            Long nextSupplierId = supplierWritable || payment.getSupplier() == null
                    ? supplierId
                    : payment.getSupplier().getId();
            Long nextAccountId = accountWritable || payment.getBankAccountId() == null
                    ? bankAccountId
                    : payment.getBankAccountId();
            applyPayee(payment, nextSupplierId, nextAccountId);
        }
        if (writable(modes, "payMethod") || payment.getPayMethod() == null) {
            payment.setPayMethod(PayMethod.from(payMethod));
        }
        if (writable(modes, "expectedPayDate") || payment.getExpectedPayDate() == null) {
            payment.setExpectedPayDate(parseDate(expectedPayDate));
        }
    }

    private void applyPayee(Payment payment, Long supplierId, Long bankAccountId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> ApiException.badRequest("请选择供应商"));
        supplier.getBankAccounts().size();
        if (supplier.getBankAccounts().isEmpty()) {
            throw ApiException.badRequest("必须先在供应商资料补账户");
        }
        if (bankAccountId == null) {
            throw ApiException.badRequest("请选择收款账户");
        }
        SupplierBankAccount account = supplier.getBankAccounts().stream()
                .filter(item -> bankAccountId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("所选账户不属于该供应商"));
        payment.setSupplier(supplier);
        payment.setBankAccountId(account.getId());
        payment.setPayeeName(account.getAccountName());
        payment.setBankName(account.getBankName());
        payment.setAccountNo(account.getAccountNo());
        payment.setBranch(account.getBranch());
    }

    private BankAccount requireCompanyAccount(Long accountId) {
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

    private Project optionalPublicProject(Long projectId) {
        if (projectId == null) {
            return null;
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.badRequest("项目不存在"));
        if (!project.isPublic()) {
            throw ApiException.badRequest("该内容未开放公开办理");
        }
        LinkGuard.assertProjectLinkable(project);
        return project;
    }

    private void requirePublicSupplier(Long supplierId) {
        if (supplierId == null) {
            throw ApiException.badRequest("请选择供应商");
        }
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> ApiException.badRequest("请选择供应商"));
        if (!supplier.isPublic()) {
            throw ApiException.badRequest("该内容未开放公开办理");
        }
    }

    private Project optionalProject(Long projectId, Long previousId) {
        if (projectId == null) {
            return null;
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.badRequest("项目不存在"));
        permissionService.requireOwnRecord(FormModule.PROJECT, project.getCreatedBy());
        LinkGuard.assertNewProjectLink(project, previousId);
        return project;
    }

    private static Long previousProjectId(Payment payment) {
        return payment.getProject() == null ? null : payment.getProject().getId();
    }

    private void addFiles(Payment payment, List<MultipartFile> files) {
        if (files == null) {
            return;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            FileStorageService.StoredFile stored = fileStorageService.savePaymentFile(file);
            PaymentFile attachment = new PaymentFile();
            attachment.setPayment(payment);
            attachment.setOriginalName(stored.originalName());
            attachment.setUrl(stored.url());
            attachment.setSize(stored.size());
            attachment.setContentType(stored.contentType());
            payment.getFiles().add(attachment);
        }
    }

    private PaymentView toView(Payment payment) {
        if (payment.getAccount() != null) {
            payment.getAccount().getName();
        }
        Project linked = payment.getProject();
        Long projectId = linked == null ? null : linked.getId();
        BigDecimal available = BigDecimal.ZERO;
        WorkflowInstance instance = instanceRepository
                .findByFormModuleAndBizId(FormModule.PAYMENT, payment.getId())
                .orElse(null);
        String status = instance == null ? null : instance.getStatus();
        return PaymentView.from(
                payment,
                available == null ? BigDecimal.ZERO : available,
                paymentBudgetService.paidTotal(projectId),
                status,
                workflowService.statusLabel(status)
        );
    }

    private String nextCode() {
        String prefix = "PAY" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 1; i < 1000; i++) {
            String code = prefix + String.format("%03d", i);
            if (!paymentRepository.existsByCode(code)) {
                return code;
            }
        }
        throw ApiException.badRequest("当日付款申请过多，请稍后重试");
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
            throw ApiException.badRequest("请选择期望付款日");
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest("期望付款日格式不正确");
        }
    }

    private boolean writable(Map<String, String> modes, String field) {
        if (modes == null || modes.isEmpty()) {
            return true;
        }
        return WorkflowService.EDITABLE.equals(modes.getOrDefault(field, WorkflowService.EDITABLE));
    }

    private Payment require(Long id) {
        return paymentRepository.findById(id).orElseThrow(() -> ApiException.notFound("付款申请不存在"));
    }

    @Transactional
    public PaymentView addFiles(Long id, List<MultipartFile> files) {
        Payment payment = require(id);
        permissionService.requireOwnRecord(FormModule.PAYMENT, payment.getCreatedBy());
        addFiles(payment, files);
        return toView(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentView removeFile(Long id, Long fileId) {
        Payment payment = require(id);
        permissionService.requireOwnRecord(FormModule.PAYMENT, payment.getCreatedBy());
        payment.getFiles().removeIf(item -> fileId.equals(item.getId()));
        return toView(payment);
    }
}
