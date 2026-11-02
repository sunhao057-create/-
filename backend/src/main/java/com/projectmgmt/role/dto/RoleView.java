package com.projectmgmt.role.dto;

import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.role.SysRole;
import com.projectmgmt.role.SysRolePermission;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RoleView(
        Long id,
        String code,
        String name,
        String description,
        boolean builtin,
        List<FormPermissionView> permissions
) {
    public static RoleView from(SysRole role) {
        boolean admin = PermissionService.ADMIN_CODE.equals(role.getCode());
        Map<String, SysRolePermission> stored = new LinkedHashMap<>();
        if (role.getPermissions() != null) {
            for (SysRolePermission permission : role.getPermissions()) {
                stored.put(permission.getModule(), permission);
            }
        }
        java.util.List<FormPermissionView> permissions = new java.util.ArrayList<>();
        for (FormModule module : FormModule.all()) {
            if (admin) {
                permissions.add(FormPermissionView.allAllowed(module));
                continue;
            }
            SysRolePermission permission = stored.get(module.name());
            permissions.add(permission == null ? FormPermissionView.none(module) : FormPermissionView.from(permission));
        }
        // 动态表单模块（模块名为表单 code，不在固定枚举内）
        for (Map.Entry<String, SysRolePermission> entry : stored.entrySet()) {
            if (FormModule.fromNameOrNull(entry.getKey()) == null) {
                permissions.add(FormPermissionView.from(entry.getValue()));
            }
        }
        return new RoleView(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.isBuiltin(),
                permissions
        );
    }
}
