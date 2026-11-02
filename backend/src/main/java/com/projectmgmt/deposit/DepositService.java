package com.projectmgmt.deposit;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.LinkGuard;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.finance.BankAccount;
import com.projectmgmt.finance.BankAccountRepository;
import com.projectmgmt.profile.FileStorageService;
import com.projectmgmt.project.AttachmentView;
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
public class DepositService {

    private final DepositRepository depositRepository;
    private final ProjectRepository projectRepository;
    private final BankAccountRepository accountRepository;
    private final FileStorageService fileStorageService;
    private final WorkflowService workflowService;
    private final WorkflowInstanceRepository instanceRepository;
    private final DepositSettleService settleService;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public PageResult<DepositView> list(DepositKind kind, String keyword, Long projectId, Boolean openOnly, int page, int size) {
        FormModule module = kind.module();
        Specification<Deposit> spec = (root, query, cb) -> cb.equal(root.get("kind"), kind);
        if (projectId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("project").get("id"), projectId));
        }
        if (StringUtils.hasText(keyword)) {
            spec = spec.and((root, query, cb) -> {
                if (query != null) {
                    query.distinct(true);
                }
                String like = "%" + keyword.trim().toLowerCase() + "%";
                Join<Deposit, Project> project = root.join("project", JoinType.INNER);
                Join<Deposit, BankAccount> account = root.join("account", JoinType.INNER);
                List<Predicate> ors = new ArrayList<>();
                ors.add(cb.like(cb.lower(root.get("code")), like));
                ors.add(cb.like(cb.lower(root.get("remark")), like));
                ors.add(cb.like(cb.lower(project.get("name")), like));
                ors.add(cb.like(cb.lower(project.get("code")), like));
                ors.add(cb.like(cb.lower(account.get("name")), like));
                return cb.or(ors.toArray(Predicate[]::new));
            });
        }
        spec = spec.and(DataScopeQuery.createdBy(permissionService.ownFilterUserId(module)));
        Page<Deposit> result = depositRepository.findAll(
                spec,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id"))
        );
        List<DepositView> items = result.getContent().stream()
                .map(this::toView)
                .filter(item -> openOnly == null || !openOnly || item.remainingAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (Boolean.TRUE.equals(openOnly)) {
            return new PageResult<>(items, items.size(), page, size);
        }
        return new PageResult<>(items, result.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public DepositView get(DepositKind kind, Long id) {
        Deposit item = require(kind, id);
        permissionService.requireOwnRecord(kind.module(), item.getCreatedBy());
        item.getFiles().size();
        return toView(item);
    }

    @Transactional
    public DepositView create(
            DepositKind kind,
            Long projectId,
            Long applyId,
            Long accountId,
            String amount,
            String bizDate,
            String remark,
            SysUser creator,
            List<MultipartFile> files
    ) {
        FormModule module = kind.module();
        if (!workflowService.hasPublished(module)) {
            throw ApiException.badRequest("请先在流程引擎发布" + kind.label() + "流程");
        }
        Deposit item = new Deposit();
        item.setCode(nextCode(kind));
        item.setKind(kind);
        item.setCreatedBy(creator);
        applyCore(item, kind, projectId, applyId, accountId, amount, bizDate, remark, Map.of(), false);
        addFiles(item, files);
        Deposit saved = depositRepository.save(item);
        workflowService.start(module, saved.getId());
        return toView(saved);
    }

    @Transactional
    public DepositView update(
            DepositKind kind,
            Long id,
            Long projectId,
            Long applyId,
            Long accountId,
            String amount,
            String bizDate,
            String remark,
            List<MultipartFile> files
    ) {
        Deposit item = require(kind, id);
        permissionService.requireOwnRecord(kind.module(), item.getCreatedBy());
        Map<String, String> modes = workflowService.writeModes(kind.module(), id);
        applyCore(item, kind, projectId, applyId, accountId, amount, bizDate, remark, modes, item.isLedgerPosted());
        addFiles(item, files);
        return toView(item);
    }

    @Transactional
    public void delete(DepositKind kind, Long id) {
        Deposit item = require(kind, id);
        permissionService.requireOwnRecord(kind.module(), item.getCreatedBy());
        if (kind == DepositKind.APPLY && depositRepository.existsByApply_Id(id)) {
            throw ApiException.badRequest("该保证金申请已有退回单，请先删除退回单");
        }
        settleService.restoreIfPosted(item);
        workflowService.deleteForBiz(kind.module(), id);
        depositRepository.delete(item);
    }

    private void applyCore(
            Deposit item,
            DepositKind kind,
            Long projectId,
            Long applyId,
            Long accountId,
            String amount,
            String bizDate,
            String remark,
            Map<String, String> modes,
            boolean posted
    ) {
        if (writable(modes, "projectId")) {
            item.setProject(requireProject(projectId, previousProjectId(item)));
        } else if (item.getProject() == null) {
            item.setProject(requireProject(projectId, null));
        }
        if (kind == DepositKind.RETURN && writable(modes, "applyId")) {
            item.setApply(requireApply(applyId, item.getProject(), item.getId()));
        } else if (kind == DepositKind.RETURN && item.getApply() == null) {
            item.setApply(requireApply(applyId, item.getProject(), item.getId()));
        }
        if (writable(modes, "accountId") || item.getAccount() == null) {
            item.setAccount(requireAccount(accountId, kind));
        }
        if (writable(modes, "amount")) {
            item.setAmount(parseAmount(amount));
        } else if (item.getAmount() == null) {
            item.setAmount(parseAmount(amount));
        }
        if (writable(modes, "bizDate") || item.getBizDate() == null) {
            item.setBizDate(parseDate(bizDate));
        }
        if (writable(modes, "remark") || item.getRemark() == null) {
            item.setRemark(blankToNull(remark));
        }
        if (kind == DepositKind.RETURN) {
            assertRemaining(item.getApply(), item.getAmount(), item.getId());
        }
    }

    private Deposit requireApply(Long applyId, Project project, Long excludeReturnId) {
        if (applyId == null) {
            throw ApiException.badRequest("请选择保证金申请单");
        }
        Deposit apply = depositRepository.findById(applyId)
                .orElseThrow(() -> ApiException.badRequest("保证金申请单不存在"));
        if (apply.getKind() != DepositKind.APPLY) {
            throw ApiException.badRequest("请选择保证金申请单");
        }
        if (!apply.isLedgerPosted()) {
            throw ApiException.badRequest("请待保证金申请审批入账后再办理退回");
        }
        if (project != null && !project.getId().equals(apply.getProject().getId())) {
            throw ApiException.badRequest("退回单须关联同一项目");
        }
        permissionService.requireOwnRecord(FormModule.DEPOSIT_APPLY, apply.getCreatedBy());
        if (remainingOf(apply, excludeReturnId).compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("该申请单保证金已退完");
        }
        return apply;
    }

    private void assertRemaining(Deposit apply, BigDecimal amount, Long excludeReturnId) {
        if (apply == null) {
            return;
        }
        BigDecimal remaining = remainingOf(apply, excludeReturnId);
        if (amount.compareTo(remaining) > 0) {
            throw ApiException.badRequest("退回金额不能大于剩余保证金 " + remaining.toPlainString());
        }
    }

    private BigDecimal remainingOf(Deposit apply, Long excludeReturnId) {
        BigDecimal returned = BigDecimal.ZERO;
        for (Deposit item : depositRepository.findByApply_Id(apply.getId())) {
            if (excludeReturnId != null && excludeReturnId.equals(item.getId())) {
                continue;
            }
            returned = returned.add(nz(item.getAmount()));
        }
        BigDecimal left = nz(apply.getAmount()).subtract(returned);
        return left.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : left;
    }

    private Project requireProject(Long projectId, Long previousId) {
        if (projectId == null) {
            throw ApiException.badRequest("请选择项目");
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.badRequest("项目不存在"));
        permissionService.requireOwnRecord(FormModule.PROJECT, project.getCreatedBy());
        LinkGuard.assertNewProjectLink(project, previousId);
        return project;
    }

    private BankAccount requireAccount(Long accountId, DepositKind kind) {
        if (accountId == null) {
            throw ApiException.badRequest(kind == DepositKind.APPLY ? "请选择付款账户" : "请选择收款账户");
        }
        BankAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.badRequest("银行账户不存在"));
        if (!account.isEnabled()) {
            throw ApiException.badRequest("该银行账户已停用");
        }
        return account;
    }

    private void addFiles(Deposit item, List<MultipartFile> files) {
        if (files == null) {
            return;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            FileStorageService.StoredFile stored = fileStorageService.saveDepositFile(file);
            DepositFile attachment = new DepositFile();
            attachment.setDeposit(item);
            attachment.setOriginalName(stored.originalName());
            attachment.setUrl(stored.url());
            attachment.setSize(stored.size());
            attachment.setContentType(stored.contentType());
            item.getFiles().add(attachment);
        }
    }

    private DepositView toView(Deposit item) {
        Project project = item.getProject();
        if (project != null) {
            project.getCustomer();
            project.getManager();
            project.getRegion();
            project.getRegionLeader();
        }
        BankAccount account = item.getAccount();
        WorkflowInstance instance = instanceRepository
                .findByFormModuleAndBizId(item.getKind().module(), item.getId())
                .orElse(null);
        String status = instance == null ? null : instance.getStatus();
        BigDecimal remaining = item.getKind() == DepositKind.APPLY
                ? remainingOf(item, null)
                : remainingOf(item.getApply() == null ? item : item.getApply(), null);
        boolean returned = item.getKind() == DepositKind.APPLY && remaining.compareTo(BigDecimal.ZERO) <= 0 && item.isLedgerPosted();
        return new DepositView(
                item.getId(),
                item.getCode(),
                item.getKind().name(),
                item.getKind().label(),
                project == null ? null : project.getId(),
                project == null ? null : project.getName(),
                project == null ? null : project.getCode(),
                project == null || project.getCustomer() == null ? null : project.getCustomer().getCompanyName(),
                project == null || project.getCustomer() == null ? null : project.getCustomer().getShortName(),
                project == null ? null : project.getCreatedAt(),
                project == null ? null : project.getCompanyName(),
                project == null || project.getRegion() == null ? null : project.getRegion().getName(),
                project == null || project.getManager() == null ? null : project.getManager().getDisplayName(),
                project == null || project.getRegionLeader() == null ? null : project.getRegionLeader().getDisplayName(),
                project == null ? null : project.getOverview(),
                item.getApply() == null ? null : item.getApply().getId(),
                item.getApply() == null ? null : item.getApply().getCode(),
                account == null ? null : account.getId(),
                account == null ? null : account.getName(),
                account == null ? null : account.getAccountName(),
                account == null ? null : account.getBankName(),
                account == null ? null : account.getAccountNo(),
                item.getAmount(),
                item.getBizDate(),
                item.getRemark(),
                item.isLedgerPosted(),
                returned,
                remaining,
                status,
                workflowService.statusLabel(status),
                item.getFiles().stream()
                        .map(file -> new AttachmentView(
                                file.getId(),
                                file.getOriginalName(),
                                file.getUrl(),
                                file.getSize(),
                                file.getContentType()
                        ))
                        .toList(),
                item.getCreatedAt()
        );
    }

    private Deposit require(DepositKind kind, Long id) {
        Deposit item = depositRepository.findById(id).orElseThrow(() -> ApiException.notFound(kind.label() + "不存在"));
        if (item.getKind() != kind) {
            throw ApiException.notFound(kind.label() + "不存在");
        }
        return item;
    }

    @Transactional
    public DepositView addFiles(DepositKind kind, Long id, List<MultipartFile> files) {
        Deposit item = require(kind, id);
        permissionService.requireOwnRecord(kind.module(), item.getCreatedBy());
        addFiles(item, files);
        return toView(depositRepository.save(item));
    }

    @Transactional
    public DepositView removeFile(DepositKind kind, Long id, Long fileId) {
        Deposit item = require(kind, id);
        permissionService.requireOwnRecord(kind.module(), item.getCreatedBy());
        item.getFiles().removeIf(file -> fileId.equals(file.getId()));
        return toView(item);
    }

    private String nextCode(DepositKind kind) {
        String prefix = kind.codePrefix() + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 1; i < 1000; i++) {
            String code = prefix + String.format("%03d", i);
            if (!depositRepository.existsByCode(code)) {
                return code;
            }
        }
        throw ApiException.badRequest("当日" + kind.label() + "过多，请稍后重试");
    }

    private static Long previousProjectId(Deposit item) {
        return item.getProject() == null ? null : item.getProject().getId();
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
            throw ApiException.badRequest("请选择日期");
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest("日期格式不正确");
        }
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
