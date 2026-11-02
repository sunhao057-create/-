package com.projectmgmt.org;

import com.projectmgmt.common.ApiResponse;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
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
@RequestMapping("/api/org/departments")
@RequiredArgsConstructor
public class OrgController {

    private final OrgService orgService;
    private final PermissionService permissionService;

    @GetMapping
    public ApiResponse<List<OrgDepartmentView>> list() {
        permissionService.require(FormModule.ORG, "VIEW");
        return ApiResponse.ok(orgService.list());
    }

    @PostMapping
    public ApiResponse<OrgDepartmentView> create(@Valid @RequestBody UpsertDepartmentRequest request) {
        permissionService.require(FormModule.ORG, "CREATE");
        return ApiResponse.ok(orgService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<OrgDepartmentView> update(
            @PathVariable Long id,
            @Valid @RequestBody UpsertDepartmentRequest request
    ) {
        permissionService.require(FormModule.ORG, "EDIT");
        return ApiResponse.ok(orgService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        permissionService.require(FormModule.ORG, "DELETE");
        orgService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<OrgMemberView>> members(@PathVariable Long id) {
        permissionService.require(FormModule.ORG, "VIEW");
        return ApiResponse.ok(orgService.members(id));
    }

    @PostMapping("/{id}/members")
    public ApiResponse<List<OrgMemberView>> assign(
            @PathVariable Long id,
            @Valid @RequestBody AssignMemberRequest request
    ) {
        permissionService.require(FormModule.ORG, "EDIT");
        return ApiResponse.ok(orgService.assignMember(id, request));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<List<OrgMemberView>> remove(@PathVariable Long id, @PathVariable Long userId) {
        permissionService.require(FormModule.ORG, "EDIT");
        return ApiResponse.ok(orgService.removeMember(id, userId));
    }
}
