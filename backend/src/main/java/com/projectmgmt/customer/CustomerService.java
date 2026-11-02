package com.projectmgmt.customer;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.PublicFilters;
import com.projectmgmt.customer.dto.BankAccountRequest;
import com.projectmgmt.customer.dto.ContactRequest;
import com.projectmgmt.customer.dto.UpsertCustomerRequest;
import com.projectmgmt.contract.ContractRepository;
import com.projectmgmt.project.ProjectRepository;
import com.projectmgmt.role.DataScopeQuery;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.workflow.WorkflowService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final ProjectRepository projectRepository;
    private final ContractRepository contractRepository;
    private final WorkflowService workflowService;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public List<CustomerView> list(String keyword, String publicFilter) {
        Specification<Customer> spec = (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return cb.conjunction();
            }
            String like = "%" + keyword.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("shortName")), like),
                    cb.like(cb.lower(root.get("companyName")), like),
                    cb.like(cb.lower(root.get("code")), like)
            );
        };
        spec = spec.and(PublicFilters.byFlag(publicFilter));
        spec = spec.and(DataScopeQuery.createdBy(permissionService.ownFilterUserId(FormModule.CUSTOMER)));
        return customerRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(CustomerView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerView get(Long id) {
        Customer customer = require(id);
        permissionService.requireOwnRecord(FormModule.CUSTOMER, customer.getCreatedBy());
        return CustomerView.detail(customer);
    }

    @Transactional
    public CustomerView create(UpsertCustomerRequest request, SysUser creator) {
        String code = request.code().trim();
        if (customerRepository.existsByCode(code)) {
            throw ApiException.badRequest("客户编码已存在");
        }
        Customer customer = new Customer();
        customer.setCreatedBy(creator);
        apply(customer, request, code, Map.of());
        Customer saved = customerRepository.saveAndFlush(customer);
        workflowService.start(FormModule.CUSTOMER, saved.getId());
        return CustomerView.detail(saved);
    }

    @Transactional
    public CustomerView update(Long id, UpsertCustomerRequest request) {
        Customer customer = require(id);
        permissionService.requireOwnRecord(FormModule.CUSTOMER, customer.getCreatedBy());
        Map<String, String> modes = workflowService.writeModes(FormModule.CUSTOMER, id);
        String code = customer.getCode();
        if (writable(modes, "code")) {
            if (!StringUtils.hasText(request.code())) {
                throw ApiException.badRequest("客户编码不能为空");
            }
            code = request.code().trim();
            if (customerRepository.existsByCodeAndIdNot(code, id)) {
                throw ApiException.badRequest("客户编码已存在");
            }
        }
        apply(customer, request, code, modes);
        customerRepository.flush();
        return CustomerView.detail(customer);
    }

    @Transactional
    public void delete(Long id) {
        Customer customer = require(id);
        permissionService.requireOwnRecord(FormModule.CUSTOMER, customer.getCreatedBy());
        if (contractRepository.existsByCustomer_Id(id)) {
            throw ApiException.badRequest("该客户已关联合同，不能删除");
        }
        if (projectRepository.existsByCustomer_Id(id)) {
            throw ApiException.badRequest("该客户已关联项目立项，不能删除");
        }
        customerRepository.delete(customer);
    }

    private Customer require(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("客户不存在"));
    }

    private void apply(Customer customer, UpsertCustomerRequest request, String code, Map<String, String> modes) {
        if (writable(modes, "shortName")) {
            if (!StringUtils.hasText(request.shortName())) {
                throw ApiException.badRequest("客户简称不能为空");
            }
            customer.setShortName(request.shortName().trim());
        }
        if (writable(modes, "companyName")) {
            if (!StringUtils.hasText(request.companyName())) {
                throw ApiException.badRequest("公司名称不能为空");
            }
            customer.setCompanyName(request.companyName().trim());
        }
        if (writable(modes, "code")) {
            customer.setCode(code);
        }
        if (writable(modes, "level")) {
            if (!StringUtils.hasText(request.level())) {
                throw ApiException.badRequest("请选择客户等级");
            }
            customer.setLevel(request.level().trim());
        }
        if (writable(modes, "contacts")) {
            replaceContacts(customer, request.contacts());
        }
        if (writable(modes, "bankAccounts")) {
            replaceBankAccounts(customer, request.bankAccounts());
        }
        if (request.isPublic() != null) {
            customer.setPublic(request.isPublic());
        }
    }

    private boolean writable(Map<String, String> modes, String field) {
        return WorkflowService.EDITABLE.equals(modes.getOrDefault(field, WorkflowService.EDITABLE));
    }

    private void replaceContacts(Customer customer, List<ContactRequest> requests) {
        customer.getContacts().clear();
        if (requests == null || requests.isEmpty()) {
            return;
        }
        boolean primaryAssigned = false;
        int order = 0;
        for (ContactRequest item : requests) {
            boolean primary = Boolean.TRUE.equals(item.primaryContact()) && !primaryAssigned;
            if (primary) {
                primaryAssigned = true;
            }
            CustomerContact contact = new CustomerContact();
            contact.setCustomer(customer);
            contact.setName(item.name().trim());
            contact.setTitle(blankToNull(item.title()));
            contact.setPhone(blankToNull(item.phone()));
            contact.setEmail(blankToNull(item.email()));
            contact.setPrimaryContact(primary);
            contact.setSortOrder(order++);
            customer.getContacts().add(contact);
        }
        if (!primaryAssigned && !customer.getContacts().isEmpty()) {
            customer.getContacts().get(0).setPrimaryContact(true);
        }
    }

    private void replaceBankAccounts(Customer customer, List<BankAccountRequest> requests) {
        customer.getBankAccounts().clear();
        if (requests == null || requests.isEmpty()) {
            return;
        }
        boolean primaryAssigned = false;
        int order = 0;
        for (BankAccountRequest item : requests) {
            boolean primary = Boolean.TRUE.equals(item.primaryAccount()) && !primaryAssigned;
            if (primary) {
                primaryAssigned = true;
            }
            CustomerBankAccount account = new CustomerBankAccount();
            account.setCustomer(customer);
            account.setAccountName(item.accountName().trim());
            account.setBankName(item.bankName().trim());
            account.setAccountNo(item.accountNo().trim());
            account.setBranch(blankToNull(item.branch()));
            account.setPrimaryAccount(primary);
            account.setSortOrder(order++);
            customer.getBankAccounts().add(account);
        }
        if (!primaryAssigned && !customer.getBankAccounts().isEmpty()) {
            customer.getBankAccounts().get(0).setPrimaryAccount(true);
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
