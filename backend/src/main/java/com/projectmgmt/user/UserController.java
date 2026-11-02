package com.projectmgmt.user;

import com.projectmgmt.common.ApiResponse;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.security.CurrentUser;
import com.projectmgmt.user.dto.CreateUserRequest;
import com.projectmgmt.user.dto.LoginLogView;
import com.projectmgmt.user.dto.ResetPasswordRequest;
import com.projectmgmt.user.dto.UpdateStatusRequest;
import com.projectmgmt.user.dto.UpdateUserRequest;
import com.projectmgmt.user.dto.UserView;
import jakarta.validation.Valid;
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
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CurrentUser currentUser;
    private final PermissionService permissionService;

    @GetMapping
    public ApiResponse<PageResult<UserView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        permissionService.require(FormModule.USER, "VIEW");
        return ApiResponse.ok(userService.list(keyword, status, page, size));
    }

    @PostMapping
    public ApiResponse<UserView> create(@Valid @RequestBody CreateUserRequest request) {
        permissionService.require(FormModule.USER, "CREATE");
        return ApiResponse.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserView> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        permissionService.require(FormModule.USER, "EDIT");
        return ApiResponse.ok(userService.update(id, request));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        permissionService.require(FormModule.USER, "EDIT");
        userService.updateStatus(id, request.status(), currentUser.principal().getId());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        permissionService.require(FormModule.USER, "EDIT");
        userService.resetPassword(id, request.password());
        return ApiResponse.ok();
    }

    @GetMapping("/login-logs")
    public ApiResponse<PageResult<LoginLogView>> loginLogs(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        permissionService.require(FormModule.USER, "VIEW");
        return ApiResponse.ok(userService.loginLogs(keyword, page, size));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        permissionService.require(FormModule.USER, "DELETE");
        userService.delete(id, currentUser.principal().getId());
        return ApiResponse.ok();
    }
}
