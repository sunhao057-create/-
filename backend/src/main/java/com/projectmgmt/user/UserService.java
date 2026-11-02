package com.projectmgmt.user;

import com.projectmgmt.auth.AuthService;
import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.org.OrgService;
import com.projectmgmt.project.ProjectRepository;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.RoleService;
import com.projectmgmt.role.PermissionService;
import java.util.Objects;
import com.projectmgmt.user.dto.CreateUserRequest;
import com.projectmgmt.user.dto.LoginLogView;
import com.projectmgmt.user.dto.UpdateUserRequest;
import com.projectmgmt.user.dto.UserView;
import com.projectmgmt.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final LoginLogRepository loginLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleService roleService;
    private final PermissionService permissionService;
    private final ProjectRepository projectRepository;
    private final OrgService orgService;
    private final WorkflowService workflowService;

    public UserService(
            UserRepository userRepository,
            LoginLogRepository loginLogRepository,
            PasswordEncoder passwordEncoder,
            RoleService roleService,
            PermissionService permissionService,
            ProjectRepository projectRepository,
            OrgService orgService,
            @org.springframework.context.annotation.Lazy WorkflowService workflowService
    ) {
        this.userRepository = userRepository;
        this.loginLogRepository = loginLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleService = roleService;
        this.permissionService = permissionService;
        this.projectRepository = projectRepository;
        this.orgService = orgService;
        this.workflowService = workflowService;
    }

    @Transactional(readOnly = true)
    public PageResult<UserView> list(String keyword, UserStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id"));
        Specification<SysUser> spec = (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("displayName")), like)
                ));
            }
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            return predicate;
        };
        Page<SysUser> result = userRepository.findAll(spec, pageable);
        return new PageResult<>(
                result.map(user -> permissionService.toUserView(user, true)).getContent(),
                result.getTotalElements(),
                page,
                size
        );
    }

    @Transactional
    public UserView create(CreateUserRequest request) {
        AuthService.validatePassword(request.password());
        if (userRepository.existsByUsername(request.username())) {
            throw ApiException.badRequest("用户名已存在");
        }
        if (PermissionService.PUBLIC_GUEST_CODE.equalsIgnoreCase(request.role())) {
            throw ApiException.badRequest("不能把账号设为公开访客");
        }
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPasswordPlain(request.password());
        user.setDisplayName(request.displayName().trim());
        user.setRole(roleService.requireByCode(request.role()).getCode());
        user.setStatus(UserStatus.ENABLED);
        user.setMustChangePassword(true);
        user.setPosition(blankToNull(request.position()));
        user.setJobLevel(blankToNull(request.jobLevel()));
        user.setAccountType(normalizeAccountType(request.accountType()));
        applyDepartmentIfAllowed(user, request.departmentId(), request.deptLeader(), true);
        return permissionService.toUserView(userRepository.save(user));
    }

    @Transactional
    public UserView update(Long id, UpdateUserRequest request) {
        SysUser user = requireUser(id);
        user.setDisplayName(request.displayName().trim());
        if (request.role() != null) {
            if (PermissionService.PUBLIC_GUEST_CODE.equalsIgnoreCase(request.role())) {
                throw ApiException.badRequest("不能把账号设为公开访客");
            }
            user.setRole(roleService.requireByCode(request.role()).getCode());
        }
        if (request.position() != null) {
            user.setPosition(blankToNull(request.position()));
        }
        if (request.jobLevel() != null) {
            user.setJobLevel(blankToNull(request.jobLevel()));
        }
        if (request.accountType() != null) {
            user.setAccountType(normalizeAccountType(request.accountType()));
        }
        applyDepartmentIfAllowed(user, request.departmentId(), request.deptLeader(), false);
        UserView view = permissionService.toUserView(user);
        workflowService.reassignPendingOrgTasks();
        return view;
    }

    @Transactional
    public void updateStatus(Long id, UserStatus status, Long operatorId) {
        if (id.equals(operatorId)) {
            throw ApiException.badRequest("不能停用自己的账号");
        }
        SysUser user = requireUser(id);
        user.setStatus(status);
    }

    @Transactional
    public void resetPassword(Long id, String password) {
        AuthService.validatePassword(password);
        SysUser user = requireUser(id);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPasswordPlain(password);
        user.setMustChangePassword(true);
    }

    public PageResult<LoginLogView> loginLogs(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size);
        Page<SysLoginLog> result;
        if (StringUtils.hasText(keyword)) {
            result = loginLogRepository.findByUsernameContainingIgnoreCaseOrderByCreatedAtDesc(keyword.trim(), pageable);
        } else {
            result = loginLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return new PageResult<>(
                result.map(LoginLogView::from).getContent(),
                result.getTotalElements(),
                page,
                size
        );
    }

    public PageResult<LoginLogView> myLoginLogs(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size);
        Page<SysLoginLog> result = loginLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return new PageResult<>(
                result.map(LoginLogView::from).getContent(),
                result.getTotalElements(),
                page,
                size
        );
    }

    private String normalizeAccountType(String value) {
        if (value == null || value.isBlank() || "INTERNAL".equalsIgnoreCase(value)) {
            return "INTERNAL";
        }
        if ("EXTERNAL".equalsIgnoreCase(value)) {
            return "EXTERNAL";
        }
        throw ApiException.badRequest("账号类型只能是内部或外部");
    }

    private void applyDepartmentIfAllowed(SysUser user, Long departmentId, Boolean deptLeader, boolean creating) {
        Long currentId = user.getDepartment() == null ? null : user.getDepartment().getId();
        boolean currentLeader = user.isDeptLeader();
        boolean nextLeader = Boolean.TRUE.equals(deptLeader);
        if (creating) {
            if (departmentId == null && !nextLeader) {
                return;
            }
            permissionService.require(FormModule.ORG, "EDIT");
            orgService.applyUserDepartment(user, departmentId, deptLeader);
            return;
        }
        if (Objects.equals(currentId, departmentId) && currentLeader == nextLeader) {
            return;
        }
        permissionService.require(FormModule.ORG, "EDIT");
        orgService.applyUserDepartment(user, departmentId, deptLeader);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private SysUser requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.badRequest("用户不存在"));
    }

    @Transactional
    public void delete(Long id, Long operatorId) {
        if (id.equals(operatorId)) {
            throw ApiException.badRequest("不能删除自己的账号");
        }
        SysUser user = requireUser(id);
        if ("ADMIN".equals(user.getRole()) && userRepository.countByRole("ADMIN") <= 1) {
            throw ApiException.badRequest("不能删除最后一个管理员");
        }
        if (projectRepository.existsByManager_Id(id)
                || projectRepository.existsByCreatedBy_Id(id)
                || projectRepository.existsByRegionLeader_Id(id)) {
            throw ApiException.badRequest("该账号已关联项目，不能删除");
        }
        loginLogRepository.deleteByUserId(id);
        userRepository.delete(user);
    }
}
