package com.projectmgmt.customer;

import com.projectmgmt.common.ApiResponse;
import com.projectmgmt.contract.ContractService;
import com.projectmgmt.contract.ContractView;
import com.projectmgmt.customer.dto.UpsertCustomerRequest;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final ContractService contractService;
    private final PermissionService permissionService;
    private final CurrentUser currentUser;

    @GetMapping
    public ApiResponse<List<CustomerView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String publicFilter
    ) {
        permissionService.requireAnyView();
        return ApiResponse.ok(customerService.list(keyword, publicFilter));
    }

    @GetMapping("/{id}")
    public ApiResponse<CustomerView> get(@PathVariable Long id) {
        permissionService.require(FormModule.CUSTOMER, "VIEW");
        return ApiResponse.ok(customerService.get(id));
    }

    @GetMapping("/{id}/contracts")
    public ApiResponse<List<ContractView>> contracts(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean linkable
    ) {
        permissionService.requireAny("VIEW", FormModule.CUSTOMER, FormModule.CONTRACT);
        return ApiResponse.ok(contractService.listByCustomer(id, linkable));
    }

    @PostMapping
    public ApiResponse<CustomerView> create(@Valid @RequestBody UpsertCustomerRequest request) {
        permissionService.require(FormModule.CUSTOMER, "CREATE");
        return ApiResponse.ok(customerService.create(request, currentUser.entity()));
    }

    @PutMapping("/{id}")
    public ApiResponse<CustomerView> update(
            @PathVariable Long id,
            @Valid @RequestBody UpsertCustomerRequest request
    ) {
        permissionService.require(FormModule.CUSTOMER, "EDIT");
        return ApiResponse.ok(customerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        permissionService.require(FormModule.CUSTOMER, "DELETE");
        customerService.delete(id);
        return ApiResponse.ok();
    }
}
