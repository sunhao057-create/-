package com.projectmgmt.supplier;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.PublicFilters;
import com.projectmgmt.role.DataScopeQuery;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.supplier.dto.BankAccountRequest;
import com.projectmgmt.supplier.dto.ContactRequest;
import com.projectmgmt.supplier.dto.UpsertSupplierRequest;
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
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final WorkflowService workflowService;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public List<SupplierView> list(String keyword, String publicFilter) {
        Specification<Supplier> spec = (root, query, cb) -> {
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
        spec = spec.and(DataScopeQuery.createdBy(permissionService.ownFilterUserId(FormModule.SUPPLIER)));
        return supplierRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(SupplierView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierView get(Long id) {
        Supplier supplier = require(id);
        permissionService.requireOwnRecord(FormModule.SUPPLIER, supplier.getCreatedBy());
        return SupplierView.detail(supplier);
    }

    @Transactional
    public SupplierView create(UpsertSupplierRequest request, SysUser creator) {
        String code = request.code().trim();
        if (supplierRepository.existsByCode(code)) {
            throw ApiException.badRequest("供应商编码已存在");
        }
        Supplier supplier = new Supplier();
        supplier.setCreatedBy(creator);
        apply(supplier, request, code, Map.of());
        Supplier saved = supplierRepository.saveAndFlush(supplier);
        workflowService.start(FormModule.SUPPLIER, saved.getId());
        return SupplierView.detail(saved);
    }

    @Transactional
    public SupplierView update(Long id, UpsertSupplierRequest request) {
        Supplier supplier = require(id);
        permissionService.requireOwnRecord(FormModule.SUPPLIER, supplier.getCreatedBy());
        Map<String, String> modes = workflowService.writeModes(FormModule.SUPPLIER, id);
        String code = supplier.getCode();
        if (writable(modes, "code")) {
            if (!StringUtils.hasText(request.code())) {
                throw ApiException.badRequest("供应商编码不能为空");
            }
            code = request.code().trim();
            if (supplierRepository.existsByCodeAndIdNot(code, id)) {
                throw ApiException.badRequest("供应商编码已存在");
            }
        }
        apply(supplier, request, code, modes);
        supplierRepository.flush();
        return SupplierView.detail(supplier);
    }

    @Transactional
    public void delete(Long id) {
        Supplier supplier = require(id);
        permissionService.requireOwnRecord(FormModule.SUPPLIER, supplier.getCreatedBy());
        supplierRepository.delete(supplier);
    }

    private Supplier require(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("供应商不存在"));
    }

    private void apply(Supplier supplier, UpsertSupplierRequest request, String code, Map<String, String> modes) {
        if (writable(modes, "shortName")) {
            if (!StringUtils.hasText(request.shortName())) {
                throw ApiException.badRequest("供应商简称不能为空");
            }
            supplier.setShortName(request.shortName().trim());
        }
        if (writable(modes, "companyName")) {
            if (!StringUtils.hasText(request.companyName())) {
                throw ApiException.badRequest("公司名称不能为空");
            }
            supplier.setCompanyName(request.companyName().trim());
        }
        if (writable(modes, "code")) {
            supplier.setCode(code);
        }
        if (writable(modes, "category")) {
            if (!StringUtils.hasText(request.category())) {
                throw ApiException.badRequest("请选择供应商类型");
            }
            supplier.setCategory(request.category().trim());
        }
        if (writable(modes, "contacts")) {
            replaceContacts(supplier, request.contacts());
        }
        if (writable(modes, "bankAccounts")) {
            replaceBankAccounts(supplier, request.bankAccounts());
        }
        if (request.isPublic() != null) {
            supplier.setPublic(request.isPublic());
        }
    }

    private boolean writable(Map<String, String> modes, String field) {
        return WorkflowService.EDITABLE.equals(modes.getOrDefault(field, WorkflowService.EDITABLE));
    }

    private void replaceContacts(Supplier supplier, List<ContactRequest> requests) {
        supplier.getContacts().clear();
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
            SupplierContact contact = new SupplierContact();
            contact.setSupplier(supplier);
            contact.setName(item.name().trim());
            contact.setTitle(blankToNull(item.title()));
            contact.setPhone(blankToNull(item.phone()));
            contact.setEmail(blankToNull(item.email()));
            contact.setPrimaryContact(primary);
            contact.setSortOrder(order++);
            supplier.getContacts().add(contact);
        }
        if (!primaryAssigned && !supplier.getContacts().isEmpty()) {
            supplier.getContacts().get(0).setPrimaryContact(true);
        }
    }

    private void replaceBankAccounts(Supplier supplier, List<BankAccountRequest> requests) {
        supplier.getBankAccounts().clear();
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
            SupplierBankAccount account = new SupplierBankAccount();
            account.setSupplier(supplier);
            account.setAccountName(item.accountName().trim());
            account.setBankName(item.bankName().trim());
            account.setAccountNo(item.accountNo().trim());
            account.setBranch(blankToNull(item.branch()));
            account.setPrimaryAccount(primary);
            account.setSortOrder(order++);
            supplier.getBankAccounts().add(account);
        }
        if (!primaryAssigned && !supplier.getBankAccounts().isEmpty()) {
            supplier.getBankAccounts().get(0).setPrimaryAccount(true);
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
