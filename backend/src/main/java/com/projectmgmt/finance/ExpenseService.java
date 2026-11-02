package com.projectmgmt.finance;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.LinkGuard;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.profile.FileStorageService;
import com.projectmgmt.project.Project;
import com.projectmgmt.project.ProjectRepository;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final BankAccountRepository accountRepository;
    private final ProjectRepository projectRepository;
    private final FileStorageService fileStorageService;
    private final WorkflowService workflowService;
    private final WorkflowInstanceRepository instanceRepository;
    private final ExpenseBudgetService expenseBudgetService;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public PageResult<ExpenseView> list(String keyword, int page, int size) {
        Specification<Expense> spec = (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            if (!StringUtils.hasText(keyword)) {
                return cb.conjunction();
            }
            String like = "%" + keyword.trim().toLowerCase() + "%";
            Join<Expense, Project> project = root.join("project", JoinType.LEFT);
            Join<Expense, BankAccount> account = root.join("account", JoinType.INNER);
            List<Predicate> ors = new ArrayList<>();
            ors.add(cb.like(cb.lower(root.get("code")), like));
            ors.add(cb.like(cb.lower(root.get("reason")), like));
            ors.add(cb.like(cb.lower(account.get("name")), like));
            ors.add(cb.like(cb.lower(project.get("name")), like));
            ors.add(cb.like(cb.lower(project.get("code")), like));
            return cb.or(ors.toArray(Predicate[]::new));
        };
        spec = spec.and(DataScopeQuery.createdBy(permissionService.ownFilterUserId(FormModule.EXPENSE)));
        Page<Expense> result = expenseRepository.findAll(
                spec,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id"))
        );
        return new PageResult<>(result.map(this::toView).getContent(), result.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public ExpenseView get(Long id) {
        Expense expense = require(id);
        permissionService.requireOwnRecord(FormModule.EXPENSE, expense.getCreatedBy());
        expense.getFiles().size();
        return toView(expense);
    }

    @Transactional
    public ExpenseView create(
            Long projectId,
            Long accountId,
            String amount,
            String reason,
            String category,
            String expenseDate,
            String payeeName,
            String bankName,
            String accountNo,
            String branch,
            SysUser creator,
            List<MultipartFile> files
    ) {
        if (!workflowService.hasPublished(FormModule.EXPENSE)) {
            throw ApiException.badRequest("请先在流程引擎发布报销申请流程");
        }
        Expense expense = new Expense();
        expense.setCode(nextCode());
        expense.setCreatedBy(creator);
        applyCore(expense, projectId, accountId, amount, reason, category, expenseDate, payeeName, bankName, accountNo, branch, Map.of(), false);
        addFiles(expense, files);
        Expense saved = expenseRepository.save(expense);
        workflowService.start(FormModule.EXPENSE, saved.getId());
        return toView(saved);
    }

    @Transactional
    public ExpenseView update(
            Long id,
            Long projectId,
            Long accountId,
            String amount,
            String reason,
            String category,
            String expenseDate,
            String payeeName,
            String bankName,
            String accountNo,
            String branch,
            List<MultipartFile> files
    ) {
        Expense expense = require(id);
        permissionService.requireOwnRecord(FormModule.EXPENSE, expense.getCreatedBy());
        Map<String, String> modes = workflowService.writeModes(FormModule.EXPENSE, id);
        applyCore(expense, projectId, accountId, amount, reason, category, expenseDate, payeeName, bankName, accountNo, branch, modes, expense.isBudgetDeducted());
        addFiles(expense, files);
        return toView(expense);
    }

    @Transactional
    public void delete(Long id) {
        Expense expense = require(id);
        permissionService.requireOwnRecord(FormModule.EXPENSE, expense.getCreatedBy());
        expenseBudgetService.restoreIfDeducted(expense);
        workflowService.deleteForBiz(FormModule.EXPENSE, id);
        expenseRepository.delete(expense);
    }

    private void applyCore(
            Expense expense,
            Long projectId,
            Long accountId,
            String amount,
            String reason,
            String category,
            String expenseDate,
            String payeeName,
            String bankName,
            String accountNo,
            String branch,
            Map<String, String> modes,
            boolean deducted
    ) {
        if (writable(modes, "projectId")) {
            expense.setProject(optionalProject(projectId, previousProjectId(expense)));
        } else if (expense.getId() == null) {
            expense.setProject(optionalProject(projectId, null));
        }
        if (writable(modes, "accountId") || expense.getAccount() == null) {
            expense.setAccount(requireAccount(accountId));
        }
        if (writable(modes, "amount")) {
            expense.setAmount(parseAmount(amount));
        } else if (expense.getAmount() == null) {
            expense.setAmount(parseAmount(amount));
        }
        if (writable(modes, "reason") || expense.getReason() == null) {
            if (!StringUtils.hasText(reason)) {
                throw ApiException.badRequest("请填写报销事由");
            }
            expense.setReason(reason.trim());
        }
        if (writable(modes, "category") || expense.getCategory() == null) {
            expense.setCategory(ExpenseCategory.from(category));
        }
        if (writable(modes, "expenseDate") || expense.getExpenseDate() == null) {
            expense.setExpenseDate(parseDate(expenseDate));
        }
        if (writable(modes, "payeeName") || expense.getPayeeName() == null) {
            expense.setPayeeName(blankToNull(payeeName));
            expense.setBankName(blankToNull(bankName));
            expense.setAccountNo(blankToNull(accountNo));
            expense.setBranch(blankToNull(branch));
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

    private static Long previousProjectId(Expense expense) {
        return expense.getProject() == null ? null : expense.getProject().getId();
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

    private void addFiles(Expense expense, List<MultipartFile> files) {
        if (files == null) {
            return;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            FileStorageService.StoredFile stored = fileStorageService.saveExpenseFile(file);
            ExpenseFile attachment = new ExpenseFile();
            attachment.setExpense(expense);
            attachment.setOriginalName(stored.originalName());
            attachment.setUrl(stored.url());
            attachment.setSize(stored.size());
            attachment.setContentType(stored.contentType());
            expense.getFiles().add(attachment);
        }
    }

    private ExpenseView toView(Expense expense) {
        BigDecimal available = BigDecimal.ZERO;
        WorkflowInstance instance = instanceRepository
                .findByFormModuleAndBizId(FormModule.EXPENSE, expense.getId())
                .orElse(null);
        String status = instance == null ? null : instance.getStatus();
        return ExpenseView.from(
                expense,
                available == null ? BigDecimal.ZERO : available,
                status,
                workflowService.statusLabel(status)
        );
    }

    private Expense require(Long id) {
        return expenseRepository.findById(id).orElseThrow(() -> ApiException.notFound("报销申请不存在"));
    }

    @Transactional
    public ExpenseView addFiles(Long id, List<MultipartFile> files) {
        Expense expense = require(id);
        permissionService.requireOwnRecord(FormModule.EXPENSE, expense.getCreatedBy());
        addFiles(expense, files);
        return toView(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseView removeFile(Long id, Long fileId) {
        Expense expense = require(id);
        permissionService.requireOwnRecord(FormModule.EXPENSE, expense.getCreatedBy());
        expense.getFiles().removeIf(item -> fileId.equals(item.getId()));
        return toView(expense);
    }

    private String nextCode() {
        String prefix = "BX" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 1; i < 1000; i++) {
            String code = prefix + String.format("%03d", i);
            if (!expenseRepository.existsByCode(code)) {
                return code;
            }
        }
        throw ApiException.badRequest("当日报销申请过多，请稍后重试");
    }

    private static boolean writable(Map<String, String> modes, String field) {
        if (modes == null || modes.isEmpty()) {
            return true;
        }
        return WorkflowService.EDITABLE.equals(modes.getOrDefault(field, WorkflowService.EDITABLE));
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
            throw ApiException.badRequest("请选择报销日期");
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest("报销日期格式不正确");
        }
    }
}
