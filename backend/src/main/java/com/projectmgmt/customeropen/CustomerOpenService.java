package com.projectmgmt.customeropen;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.customer.Customer;
import com.projectmgmt.customer.CustomerContact;
import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.org.OrgDepartment;
import com.projectmgmt.org.OrgDepartmentRepository;
import com.projectmgmt.role.DataScopeQuery;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.workflow.WorkflowInstance;
import com.projectmgmt.workflow.WorkflowInstanceRepository;
import com.projectmgmt.workflow.WorkflowService;
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
public class CustomerOpenService {

    private static final FormModule MODULE = FormModule.CUSTOMER_APPLY;
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final CustomerOpenRepository repository;
    private final CustomerRepository customerRepository;
    private final OrgDepartmentRepository departmentRepository;
    private final WorkflowService workflowService;
    private final WorkflowInstanceRepository instanceRepository;
    private final PermissionService permissionService;

    public CustomerOpenService(
            CustomerOpenRepository repository,
            CustomerRepository customerRepository,
            OrgDepartmentRepository departmentRepository,
            @Lazy WorkflowService workflowService,
            WorkflowInstanceRepository instanceRepository,
            PermissionService permissionService
    ) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.departmentRepository = departmentRepository;
        this.workflowService = workflowService;
        this.instanceRepository = instanceRepository;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public List<CustomerOpenView> list() {
        permissionService.require(MODULE, "VIEW");
        Specification<CustomerOpen> spec = DataScopeQuery.createdBy(permissionService.ownFilterUserId(MODULE));
        return repository.findAll(spec, Sort.by(Sort.Direction.DESC, "id")).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public CustomerOpenView get(Long id) {
        permissionService.require(MODULE, "VIEW");
        CustomerOpen row = require(id);
        permissionService.requireOwnRecord(MODULE, row.getCreatedBy());
        return toView(row);
    }

    @Transactional
    public CustomerOpenView create(CustomerOpenRequest request, SysUser creator) {
        permissionService.require(MODULE, "CREATE");
        if (!workflowService.hasPublished(MODULE)) {
            throw ApiException.badRequest("请先在流程引擎发布新建客户流程");
        }
        String company = companyOf(request.companyName());
        assertCompanyFree(company, null);
        CustomerOpen row = new CustomerOpen();
        row.setCode(nextCode("KH"));
        row.setCreatedBy(creator);
        row.setSettled(false);
        fill(row, request, company);
        CustomerOpen saved = repository.saveAndFlush(row);
        workflowService.start(MODULE, saved.getId());
        return toView(saved);
    }

    @Transactional
    public CustomerOpenView update(Long id, CustomerOpenRequest request) {
        permissionService.require(MODULE, "EDIT");
        CustomerOpen row = require(id);
        permissionService.requireOwnRecord(MODULE, row.getCreatedBy());
        if (row.isSettled()) {
            throw ApiException.badRequest("已审核通过的客户不能再改公司名称");
        }
        String company = companyOf(request.companyName());
        assertCompanyFree(company, row.getId());
        fill(row, request, company);
        return toView(repository.save(row));
    }

    @Transactional
    public void delete(Long id) {
        permissionService.require(MODULE, "DELETE");
        CustomerOpen row = require(id);
        permissionService.requireOwnRecord(MODULE, row.getCreatedBy());
        if (row.isSettled()) {
            throw ApiException.badRequest("已审核通过的客户申请不能删除");
        }
        repository.delete(row);
    }

    @Transactional
    public void settle(Long id) {
        CustomerOpen row = repository.findById(id).orElse(null);
        if (row == null || row.isSettled()) {
            return;
        }
        String company = row.getCompanyName().trim();
        assertCompanyFree(company, row.getId());
        OrgDepartment dept = new OrgDepartment();
        dept.setName(company);
        dept.setParent(null);
        dept.setSortOrder(0);
        dept = departmentRepository.save(dept);
        Customer customer = new Customer();
        customer.setCompanyName(company);
        customer.setShortName(row.getShortName());
        customer.setCode(row.getCode());
        customer.setLevel(row.getLevel());
        customer.setCreatedBy(row.getCreatedBy());
        customer.setSubmitSource("APPLY");
        customer.setPublic(false);
        if (StringUtils.hasText(row.getContactName())) {
            CustomerContact contact = new CustomerContact();
            contact.setCustomer(customer);
            contact.setName(row.getContactName().trim());
            contact.setPhone(blank(row.getContactPhone()));
            contact.setPrimaryContact(true);
            contact.setSortOrder(0);
            customer.getContacts().add(contact);
        }
        customer = customerRepository.save(customer);
        row.setDepartmentId(dept.getId());
        row.setCustomerId(customer.getId());
        row.setSettled(true);
        repository.save(row);
    }

    private void fill(CustomerOpen row, CustomerOpenRequest request, String company) {
        row.setCompanyName(company);
        String shortName = StringUtils.hasText(request.shortName()) ? request.shortName().trim() : company;
        if (shortName.length() > 50) {
            shortName = shortName.substring(0, 50);
        }
        row.setShortName(shortName);
        row.setLevel(StringUtils.hasText(request.level()) ? request.level().trim() : "普通");
        row.setContactName(blank(request.contactName()));
        row.setContactPhone(blank(request.contactPhone()));
        row.setAddress(blank(request.address()));
    }

    private void assertCompanyFree(String company, Long selfId) {
        if (customerRepository.existsByCompanyName(company) || rootNamed(company)) {
            throw ApiException.badRequest("公司名称已存在，不能重复新增");
        }
        boolean pending = selfId == null
                ? repository.existsByCompanyNameAndSettledFalse(company)
                : repository.existsByCompanyNameAndIdNotAndSettledFalse(company, selfId);
        if (pending) {
            throw ApiException.badRequest("已有相同公司名称的客户申请在审核中");
        }
    }

    private boolean rootNamed(String company) {
        return departmentRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .anyMatch(dept -> dept.getParent() == null && company.equals(dept.getName()));
    }

    private String companyOf(String raw) {
        String company = raw == null ? "" : raw.trim();
        if (!StringUtils.hasText(company)) {
            throw ApiException.badRequest("请填写公司名称");
        }
        return company;
    }

    private String nextCode(String prefix) {
        String day = LocalDate.now().format(DAY);
        for (int i = 1; i < 1000; i++) {
            String code = prefix + day + String.format("%03d", i);
            if (!customerRepository.existsByCode(code) && repository.findAll().stream().noneMatch(row -> code.equals(row.getCode()))) {
                return code;
            }
        }
        throw ApiException.badRequest("客户编码生成失败");
    }

    private CustomerOpen require(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("客户申请不存在"));
    }

    private CustomerOpenView toView(CustomerOpen row) {
        WorkflowInstance instance = instanceRepository.findByFormModuleAndBizId(MODULE.name(), row.getId()).orElse(null);
        String status = instance == null ? null : instance.getStatus();
        return new CustomerOpenView(
                row.getId(),
                row.getCode(),
                row.getCompanyName(),
                row.getShortName(),
                row.getLevel(),
                row.getContactName(),
                row.getContactPhone(),
                row.getAddress(),
                row.getDepartmentId(),
                row.getCustomerId(),
                row.isSettled(),
                status,
                workflowService.statusLabel(status));
    }

    private String blank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
