package com.projectmgmt.user.dto;

import com.projectmgmt.role.dto.FormPermissionView;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserStatus;
import java.time.LocalDateTime;
import java.util.List;

public record UserView(
        Long id,
        String username,
        String password,
        String displayName,
        String avatarUrl,
        String role,
        String roleName,
        UserStatus status,
        boolean mustChangePassword,
        Long departmentId,
        String departmentName,
        boolean deptLeader,
        String position,
        String jobLevel,
        String accountType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<FormPermissionView> permissions
) {
    public static UserView from(SysUser user, String roleName, List<FormPermissionView> permissions) {
        return from(user, roleName, permissions, false);
    }

    public static UserView from(SysUser user, String roleName, List<FormPermissionView> permissions, boolean includePassword) {
        return new UserView(
                user.getId(),
                user.getUsername(),
                includePassword ? user.getPasswordPlain() : null,
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getRole(),
                roleName,
                user.getStatus(),
                user.isMustChangePassword(),
                user.getDepartment() == null ? null : user.getDepartment().getId(),
                user.getDepartment() == null ? null : user.getDepartment().getName(),
                user.isDeptLeader(),
                user.getPosition(),
                user.getJobLevel(),
                user.getAccountType() == null || user.getAccountType().isBlank() ? "INTERNAL" : user.getAccountType(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                permissions == null ? List.of() : permissions
        );
    }
}
