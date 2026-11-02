package com.projectmgmt.customeropen;

import com.projectmgmt.auth.AuthService;
import com.projectmgmt.common.ApiException;
import com.projectmgmt.customer.Customer;
import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.org.OrgDepartment;
import com.projectmgmt.org.OrgDepartmentRepository;
import com.projectmgmt.role.DataScopeQuery;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.role.RoleRepository;
import com.projectmgmt.role.RoleService;
import com.projectmgmt.role.SysRole;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.user.UserService;
import com.projectmgmt.user.dto.CreateUserRequest;
import com.projectmgmt.workflow.WorkflowInstance;
import com.projectmgmt.workflow.WorkflowInstanceRepository;
import com.projectmgmt.workflow.WorkflowService;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CustomerAccountOpenService {

    private static final FormModule MODULE = FormModule.CUSTOMER_ACCOUNT;
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CustomerAccountOpenRepository repository;
    private final CustomerRepository customerRepository;
    private final OrgDepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final RoleRepository roleRepository;
    private final RoleService roleService;
    private final WorkflowService workflowService;
    private final WorkflowInstanceRepository instanceRepository;
    private final PermissionService permissionService;

    public CustomerAccountOpenService(
            CustomerAccountOpenRepository repository,
            CustomerRepository customerRepository,
            OrgDepartmentRepository departmentRepository,
            UserRepository userRepository,
            UserService userService,
            RoleRepository roleRepository,
            RoleService roleService,
            @Lazy WorkflowService workflowService,
            WorkflowInstanceRepository instanceRepository,
            PermissionService permissionService
    ) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.roleRepository = roleRepository;
        this.roleService = roleService;
        this.workflowService = workflowService;
        this.instanceRepository = instanceRepository;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public List<CustomerAccountOpenView> list() {
        permissionService.require(MODULE, "VIEW");
        Specification<CustomerAccountOpen> spec = DataScopeQuery.createdBy(permissionService.ownFilterUserId(MODULE));
        return repository.findAll(spec, Sort.by(Sort.Direction.DESC, "id")).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public CustomerAccountOpenView get(Long id) {
        permissionService.require(MODULE, "VIEW");
        CustomerAccountOpen row = require(id);
        permissionService.requireOwnRecord(MODULE, row.getCreatedBy());
        return toView(row);
    }

    @Transactional(readOnly = true)
    public List<CustomerPick> customers() {
        permissionService.require(MODULE, "VIEW");
        return customerRepository.findAllByOrderByIdDesc().stream()
                .filter(customer -> rootOf(customer.getCompanyName()) != null)
                .map(customer -> new CustomerPick(customer.getId(), customer.getCompanyName(), customer.getShortName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RolePick> roles() {
        permissionService.require(MODULE, "EDIT");
        return roleRepository.findAll().stream()
                .filter(role -> !PermissionService.ADMIN_CODE.equals(role.getCode()))
                .filter(role -> !PermissionService.PUBLIC_GUEST_CODE.equals(role.getCode()))
                .map(role -> new RolePick(role.getCode(), role.getName()))
                .toList();
    }

    @Transactional
    public CustomerAccountOpenView create(CustomerAccountOpenRequest request, SysUser creator) {
        permissionService.require(MODULE, "CREATE");
        if (!workflowService.hasPublished(MODULE)) {
            throw ApiException.badRequest("请先在流程引擎发布新建客户账号流程");
        }
        Customer customer = readyCustomer(request.customerId());
        String username = usernameOf(request.username());
        assertUsernameFree(username, null);
        CustomerAccountOpen row = new CustomerAccountOpen();
        row.setCode(nextCode());
        row.setCreatedBy(creator);
        row.setSettled(false);
        fill(row, request, customer, username);
        CustomerAccountOpen saved = repository.saveAndFlush(row);
        workflowService.start(MODULE, saved.getId());
        return toView(saved);
    }

    @Transactional
    public CustomerAccountOpenView update(Long id, CustomerAccountOpenRequest request) {
        permissionService.require(MODULE, "EDIT");
        CustomerAccountOpen row = require(id);
        permissionService.requireOwnRecord(MODULE, row.getCreatedBy());
        if (row.isSettled()) {
            throw ApiException.badRequest("已开通的账号不能再修改");
        }
        Customer customer = readyCustomer(request.customerId());
        String username = usernameOf(request.username());
        assertUsernameFree(username, row.getId());
        fill(row, request, customer, username);
        return toView(repository.save(row));
    }

    @Transactional
    public void delete(Long id) {
        permissionService.require(MODULE, "DELETE");
        CustomerAccountOpen row = require(id);
        permissionService.requireOwnRecord(MODULE, row.getCreatedBy());
        if (row.isSettled()) {
            throw ApiException.badRequest("已开通的账号申请不能删除");
        }
        repository.delete(row);
    }

    @Transactional
    public void settle(Long id) {
        CustomerAccountOpen row = repository.findById(id).orElse(null);
        if (row == null || row.isSettled()) {
            return;
        }
        if (!StringUtils.hasText(row.getAccountType()) || !StringUtils.hasText(row.getRoleCode())) {
            throw ApiException.badRequest("审核时请指定账号类型和角色");
        }
        OrgDepartment root = rootOf(row.getCompanyName());
        if (root == null) {
            throw ApiException.badRequest("该公司还没有进入企业架构，请先完成新建客户");
        }
        if (row.getDepartmentId() == null) {
            throw ApiException.badRequest("审核时请指定该公司下的部门");
        }
        OrgDepartment dept = departmentRepository.findById(row.getDepartmentId())
                .orElseThrow(() -> ApiException.badRequest("所属部门不存在"));
        if (!companyIds(root).contains(dept.getId())) {
            throw ApiException.badRequest("所属部门必须在该公司下面");
        }
        String type = normalizeType(row.getAccountType());
        SysRole role = roleService.requireByCode(row.getRoleCode());
        if (PermissionService.ADMIN_CODE.equals(role.getCode()) || PermissionService.PUBLIC_GUEST_CODE.equals(role.getCode())) {
            throw ApiException.badRequest("不能把账号设为管理员或公开访客");
        }
        assertUsernameFree(row.getUsername(), row.getId());
        String password = password();
        userService.create(new CreateUserRequest(
                row.getUsername(),
                password,
                row.getDisplayName(),
                role.getCode(),
                null,
                false,
                null,
                null,
                type));
        SysUser created = userRepository.findByUsername(row.getUsername()).orElseThrow(() -> ApiException.badRequest("账号创建失败"));
        created.setDepartment(dept);
        userRepository.save(created);
        row.setAccountType(type);
        row.setRoleCode(role.getCode());
        row.setInitialPassword(password);
        row.setUserId(created.getId());
        row.setSettled(true);
        repository.save(row);
    }

    private void fill(CustomerAccountOpen row, CustomerAccountOpenRequest request, Customer customer, String username) {
        row.setCustomerId(customer.getId());
        row.setCompanyName(customer.getCompanyName());
        row.setDisplayName(request.displayName().trim());
        row.setUsername(username);
        row.setPhone(StringUtils.hasText(request.phone()) ? request.phone().trim() : null);
        if (StringUtils.hasText(request.accountType())) {
            row.setAccountType(normalizeType(request.accountType()));
        }
        if (StringUtils.hasText(request.roleCode())) {
            row.setRoleCode(roleService.requireByCode(request.roleCode()).getCode());
        }
        if (request.departmentId() == null) {
            row.setDepartmentId(null);
            return;
        }
        OrgDepartment root = rootOf(customer.getCompanyName());
        OrgDepartment dept = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> ApiException.badRequest("所属部门不存在"));
        if (root == null || !companyIds(root).contains(dept.getId())) {
            throw ApiException.badRequest("所属部门必须在该公司下面");
        }
        row.setDepartmentId(dept.getId());
    }

    @Transactional(readOnly = true)
    public List<DeptPick> departments(Long customerId) {
        permissionService.require(MODULE, "VIEW");
        Customer customer = readyCustomer(customerId);
        OrgDepartment root = rootOf(customer.getCompanyName());
        if (root == null) {
            return List.of();
        }
        java.util.Set<Long> ids = companyIds(root);
        return departmentRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(dept -> ids.contains(dept.getId()))
                .map(dept -> new DeptPick(
                        dept.getId(),
                        dept.getName(),
                        dept.getParent() == null ? null : dept.getParent().getId()))
                .toList();
    }

    private java.util.Set<Long> companyIds(OrgDepartment root) {
        List<OrgDepartment> all = departmentRepository.findAllByOrderBySortOrderAscIdAsc();
        java.util.Set<Long> ids = new java.util.LinkedHashSet<>();
        ids.add(root.getId());
        boolean grew = true;
        while (grew) {
            grew = false;
            for (OrgDepartment dept : all) {
                if (dept.getParent() != null && ids.contains(dept.getParent().getId()) && ids.add(dept.getId())) {
                    grew = true;
                }
            }
        }
        return ids;
    }

    private Customer readyCustomer(Long customerId) {
        if (customerId == null) {
            throw ApiException.badRequest("请选择所属客户");
        }
        Customer customer = customerRepository.findById(customerId).orElseThrow(() -> ApiException.badRequest("客户不存在"));
        if (rootOf(customer.getCompanyName()) == null) {
            throw ApiException.badRequest("该公司还没有进入企业架构，请先完成新建客户");
        }
        return customer;
    }

    private OrgDepartment rootOf(String company) {
        if (!StringUtils.hasText(company)) {
            return null;
        }
        String name = company.trim();
        return departmentRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(dept -> dept.getParent() == null && name.equals(dept.getName()))
                .findFirst()
                .orElse(null);
    }

    private void assertUsernameFree(String username, Long selfId) {
        if (userRepository.existsByUsername(username)) {
            throw ApiException.badRequest("登录账号已存在");
        }
        boolean pending = selfId == null
                ? repository.existsByUsernameAndSettledFalse(username)
                : repository.existsByUsernameAndIdNotAndSettledFalse(username, selfId);
        if (pending) {
            throw ApiException.badRequest("该登录账号已有申请在审核中");
        }
    }

    private String usernameOf(String raw) {
        String username = raw == null ? "" : raw.trim();
        AuthService.validateUsername(username);
        return username;
    }

    private String normalizeType(String value) {
        if ("EXTERNAL".equalsIgnoreCase(value)) {
            return "EXTERNAL";
        }
        if ("INTERNAL".equalsIgnoreCase(value)) {
            return "INTERNAL";
        }
        throw ApiException.badRequest("账号类型只能是内部或外部");
    }

    private String password() {
        int n = 100000 + RANDOM.nextInt(900000);
        return "Init" + n;
    }

    private String nextCode() {
        String day = LocalDate.now().format(DAY);
        for (int i = 1; i < 1000; i++) {
            String code = "ZH" + day + String.format("%03d", i);
            boolean used = repository.findAll().stream().anyMatch(row -> code.equals(row.getCode()));
            if (!used) {
                return code;
            }
        }
        throw ApiException.badRequest("账号申请编码生成失败");
    }

    private CustomerAccountOpen require(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("账号申请不存在"));
    }

    private CustomerAccountOpenView toView(CustomerAccountOpen row) {
        WorkflowInstance instance = instanceRepository.findByFormModuleAndBizId(MODULE.name(), row.getId()).orElse(null);
        String status = instance == null ? null : instance.getStatus();
        String roleName = null;
        if (StringUtils.hasText(row.getRoleCode())) {
            roleName = roleRepository.findByCode(row.getRoleCode()).map(SysRole::getName).orElse(row.getRoleCode());
        }
        return new CustomerAccountOpenView(
                row.getId(),
                row.getCode(),
                row.getCustomerId(),
                row.getCompanyName(),
                row.getDepartmentId(),
                departmentName(row.getDepartmentId()),
                row.getDisplayName(),
                row.getUsername(),
                row.getPhone(),
                row.getAccountType(),
                row.getRoleCode(),
                roleName,
                row.isSettled() ? row.getInitialPassword() : null,
                row.getUserId(),
                row.isSettled(),
                status,
                workflowService.statusLabel(status));
    }

    private String departmentName(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId).map(OrgDepartment::getName).orElse(null);
    }

    public record RolePick(String code, String name) {
    }

    public record DeptPick(Long id, String name, Long parentId) {
    }
}
