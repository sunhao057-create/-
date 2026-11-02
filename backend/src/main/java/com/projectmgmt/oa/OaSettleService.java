package com.projectmgmt.oa;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.org.OrgDepartment;
import com.projectmgmt.org.OrgService;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.user.UserSalaryItem;
import com.projectmgmt.user.UserStatus;
import com.projectmgmt.workflow.WorkflowService;
import java.util.Objects;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OaSettleService {

    private final OaRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final OrgService orgService;
    private final WorkflowService workflowService;
    private final OaLoanFinanceService loanFinance;

    public OaSettleService(
            OaRequestRepository requestRepository,
            UserRepository userRepository,
            OrgService orgService,
            @Lazy WorkflowService workflowService,
            OaLoanFinanceService loanFinance
    ) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.orgService = orgService;
        this.workflowService = workflowService;
        this.loanFinance = loanFinance;
    }

    @Transactional
    public void onWorkflowEnded(String moduleCode, Long bizId) {
        FormModule module = FormModule.fromNameOrNull(moduleCode);
        if (module == null) {
            return;
        }
        OaKind kind = OaKind.fromModule(module);
        if (kind == null || bizId == null) {
            return;
        }
        OaRequest item = requestRepository.findById(bizId).orElse(null);
        if (item == null || item.isApplied()) {
            return;
        }
        switch (kind) {
            case ONBOARD -> applyOnboard(item);
            case TRANSFER -> applyTransfer(item);
            case PROMOTION -> applyPromotion(item);
            case SALARY -> applySalary(item);
            case INTERVIEW, SEAL, QUALIFY, TRIP, LEAVE -> {
            }
            case LOAN, LOAN_PAY, LOAN_REPAY -> loanFinance.settle(item);
            case RESIGN -> applyResign(item);
        }
        item.setApplied(true);
        requestRepository.save(item);
    }

    private void applyOnboard(OaRequest item) {
        String username = item.getLoginUsername() == null ? "" : item.getLoginUsername().trim();
        if (!StringUtils.hasText(username)) {
            throw ApiException.badRequest("入职申请缺少登录账号，无法开通");
        }
        if (!StringUtils.hasText(item.getPasswordHash())) {
            throw ApiException.badRequest("入职申请缺少登录密码，无法开通");
        }
        if (userRepository.existsByUsername(username)) {
            throw ApiException.badRequest("登录账号已被占用，请先修改入职申请中的账号");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(item.getPasswordHash());
        user.setDisplayName(item.getApplicantName());
        user.setRole(PermissionService.USER_CODE);
        user.setStatus(UserStatus.ENABLED);
        user.setMustChangePassword(false);
        user.setPosition(item.getPosition());
        user.setJobLevel(item.getJobLevel());
        user.setGender(item.getGender());
        user.setMobile(item.getMobile());
        user.setIdNumber(item.getIdNumber());
        user.setHireDate(item.getExpectedDate());
        applyUserSalary(user, item);
        orgService.applyUserDepartment(
                user,
                item.getDepartment() == null ? null : item.getDepartment().getId(),
                false
        );
        SysUser saved = userRepository.save(user);
        item.setStaff(saved);
        item.setPasswordHash(null);
        workflowService.reassignPendingOrgTasks();
    }

    private void applyTransfer(OaRequest item) {
        SysUser user = requireStaff(item, "转岗申请必须选择系统账号");
        OrgDepartment target = item.getTargetDepartment();
        if (target == null) {
            throw ApiException.badRequest("转岗申请缺少转入部门");
        }
        moveDepartment(user, target);
        if (StringUtils.hasText(item.getTargetPosition())) {
            user.setPosition(item.getTargetPosition());
        }
        applyUserSalary(user, item);
        userRepository.save(user);
    }

    private void applyPromotion(OaRequest item) {
        SysUser user = requireStaff(item, "晋升申请必须选择系统账号");
        OrgDepartment targetDept = item.getTargetDepartment() == null ? item.getDepartment() : item.getTargetDepartment();
        if (targetDept != null) {
            moveDepartment(user, targetDept);
        }
        if (StringUtils.hasText(item.getTargetPosition())) {
            user.setPosition(item.getTargetPosition());
        }
        if (StringUtils.hasText(item.getTargetLevel())) {
            user.setJobLevel(item.getTargetLevel());
        }
        applyUserSalary(user, item);
        userRepository.save(user);
    }

    private void applySalary(OaRequest item) {
        SysUser user = requireStaff(item, "薪资调整必须选择系统账号");
        applyUserSalary(user, item);
        userRepository.save(user);
    }

    private void applyResign(OaRequest item) {
        SysUser user = requireStaff(item, "离职申请必须选择系统账号");
        if ("ADMIN".equals(user.getRole()) && userRepository.countByRole("ADMIN") <= 1) {
            throw ApiException.badRequest("该账号是最后一个管理员，不能停用");
        }
        user.setStatus(UserStatus.DISABLED);
        userRepository.save(user);
    }

    private void applyUserSalary(SysUser user, OaRequest item) {
        if (item.getSalary() != null) {
            user.setSalary(item.getSalary());
        }
        if (item.getSalaryLines() == null || item.getSalaryLines().isEmpty()) {
            return;
        }
        if (user.getSalaryItems() == null) {
            user.setSalaryItems(new java.util.ArrayList<>());
        }
        user.getSalaryItems().clear();
        int order = 0;
        for (OaSalaryLine line : item.getSalaryLines()) {
            UserSalaryItem row = new UserSalaryItem();
            row.setUser(user);
            row.setItemName(line.getItemName());
            row.setAmount(line.getAmount());
            row.setSortOrder(order++);
            user.getSalaryItems().add(row);
        }
        if (item.getSalary() == null) {
            user.setSalary(item.getSalaryLines().stream()
                    .map(OaSalaryLine::getAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        }
    }

    private void moveDepartment(SysUser user, OrgDepartment target) {
        Long currentId = user.getDepartment() == null ? null : user.getDepartment().getId();
        Long nextId = target == null ? null : target.getId();
        boolean moved = !Objects.equals(currentId, nextId);
        orgService.applyUserDepartment(user, nextId, moved ? false : user.isDeptLeader());
        if (moved) {
            workflowService.reassignPendingOrgTasks();
        }
    }

    private SysUser requireStaff(OaRequest item, String message) {
        if (item.getStaff() == null) {
            throw ApiException.badRequest(message);
        }
        return userRepository.findById(item.getStaff().getId())
                .orElseThrow(() -> ApiException.badRequest("系统账号不存在"));
    }
}
