package com.projectmgmt.customeropen;

import com.projectmgmt.common.ApiResponse;
import com.projectmgmt.customeropen.CustomerAccountOpenService.DeptPick;
import com.projectmgmt.customeropen.CustomerAccountOpenService.RolePick;
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
@RequestMapping("/api/customer-accounts")
@RequiredArgsConstructor
public class CustomerAccountOpenController {

    private final CustomerAccountOpenService customerAccountOpenService;
    private final CurrentUser currentUser;

    @GetMapping
    public ApiResponse<List<CustomerAccountOpenView>> list() {
        return ApiResponse.ok(customerAccountOpenService.list());
    }

    @GetMapping("/customers")
    public ApiResponse<List<CustomerPick>> customers() {
        return ApiResponse.ok(customerAccountOpenService.customers());
    }

    @GetMapping("/roles")
    public ApiResponse<List<RolePick>> roles() {
        return ApiResponse.ok(customerAccountOpenService.roles());
    }

    @GetMapping("/departments")
    public ApiResponse<List<DeptPick>> departments(@RequestParam Long customerId) {
        return ApiResponse.ok(customerAccountOpenService.departments(customerId));
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<CustomerAccountOpenView> get(@PathVariable Long id) {
        return ApiResponse.ok(customerAccountOpenService.get(id));
    }

    @PostMapping
    public ApiResponse<CustomerAccountOpenView> create(@Valid @RequestBody CustomerAccountOpenRequest request) {
        return ApiResponse.ok(customerAccountOpenService.create(request, currentUser.entity()));
    }

    @PutMapping("/{id:\\d+}")
    public ApiResponse<CustomerAccountOpenView> update(@PathVariable Long id, @Valid @RequestBody CustomerAccountOpenRequest request) {
        return ApiResponse.ok(customerAccountOpenService.update(id, request));
    }

    @DeleteMapping("/{id:\\d+}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        customerAccountOpenService.delete(id);
        return ApiResponse.ok(null);
    }
}
