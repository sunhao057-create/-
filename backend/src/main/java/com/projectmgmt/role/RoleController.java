package com.projectmgmt.role;

import com.projectmgmt.common.ApiResponse;
import com.projectmgmt.role.dto.RoleView;
import com.projectmgmt.role.dto.UpsertRoleRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final PermissionService permissionService;

    @GetMapping("/modules")
    public ApiResponse<List<Map<String, String>>> modules() {
        permissionService.require(FormModule.ROLE, "VIEW");
        return ApiResponse.ok(FormModule.all().stream()
                .map(module -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("code", module.name());
                    item.put("label", module.label());
                    return item;
                })
                .toList());
    }

    @GetMapping
    public ApiResponse<List<RoleView>> list() {
        permissionService.requireAny(FormModule.ROLE, "VIEW", FormModule.USER, "EDIT");
        return ApiResponse.ok(roleService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleView> get(@PathVariable Long id) {
        permissionService.require(FormModule.ROLE, "VIEW");
        return ApiResponse.ok(roleService.get(id));
    }

    @PostMapping
    public ApiResponse<RoleView> create(@Valid @RequestBody UpsertRoleRequest request) {
        permissionService.require(FormModule.ROLE, "CREATE");
        return ApiResponse.ok(roleService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoleView> update(@PathVariable Long id, @Valid @RequestBody UpsertRoleRequest request) {
        permissionService.require(FormModule.ROLE, "EDIT");
        return ApiResponse.ok(roleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        permissionService.require(FormModule.ROLE, "DELETE");
        roleService.delete(id);
        return ApiResponse.ok();
    }
}
