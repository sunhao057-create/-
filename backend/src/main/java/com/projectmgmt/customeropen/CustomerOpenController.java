package com.projectmgmt.customeropen;

import com.projectmgmt.common.ApiResponse;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer-opens")
@RequiredArgsConstructor
public class CustomerOpenController {

    private final CustomerOpenService customerOpenService;
    private final CurrentUser currentUser;

    @GetMapping
    public ApiResponse<List<CustomerOpenView>> list() {
        return ApiResponse.ok(customerOpenService.list());
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<CustomerOpenView> get(@PathVariable Long id) {
        return ApiResponse.ok(customerOpenService.get(id));
    }

    @PostMapping
    public ApiResponse<CustomerOpenView> create(@Valid @RequestBody CustomerOpenRequest request) {
        return ApiResponse.ok(customerOpenService.create(request, currentUser.entity()));
    }

    @PutMapping("/{id:\\d+}")
    public ApiResponse<CustomerOpenView> update(@PathVariable Long id, @Valid @RequestBody CustomerOpenRequest request) {
        return ApiResponse.ok(customerOpenService.update(id, request));
    }

    @DeleteMapping("/{id:\\d+}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        customerOpenService.delete(id);
        return ApiResponse.ok(null);
    }
}
