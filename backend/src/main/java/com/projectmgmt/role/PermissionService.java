package com.projectmgmt.role;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.role.dto.FormPermissionView;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.dto.UserView;
import com.projectmgmt.security.CurrentUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PermissionService {

    public static final String ADMIN_CODE = "ADMIN";
    public static final String USER_CODE = "USER";
    public static final String PUBLIC_GUEST_CODE = "PUBLIC_GUEST";

    private final RoleRepository roleRepository;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public Map<String, FormPermissionView> permissionsFor(String roleCode) {
        Map<String, FormPermissionView> map = new LinkedHashMap<>();
        if (ADMIN_CODE.equals(roleCode)) {
            for (FormModule module : FormModule.all()) {
                map.put(module.name(), FormPermissionView.allAllowed(module));
            }
            return map;
        }
        SysRole role = roleRepository.findByCode(roleCode).orElse(null);
        Map<String, SysRolePermission> stored = new LinkedHashMap<>();
        if (role != null) {
            for (SysRolePermission permission : role.getPermissions()) {
                stored.put(permission.getModule(), permission);
            }
        }
        for (FormModule module : FormModule.all()) {
            SysRolePermission permission = stored.get(module.name());
            map.put(module.name(), permission == null ? FormPermissionView.none(module) : FormPermissionView.from(permission));
        }
        // 动态表单模块（模块名为表单 code，不在固定枚举内）
        for (Map.Entry<String, SysRolePermission> entry : stored.entrySet()) {
            if (!map.containsKey(entry.getKey())) {
                map.put(entry.getKey(), FormPermissionView.from(entry.getValue()));
            }
        }
        return map;
    }

    public List<FormPermissionView> permissionList(String roleCode) {
        return List.copyOf(permissionsFor(roleCode).values());
    }

    @Transactional(readOnly = true)
    public UserView toUserView(SysUser user) {
        return toUserView(user, false);
    }

    @Transactional(readOnly = true)
    public UserView toUserView(SysUser user, boolean includePassword) {
        String roleName = roleRepository.findByCode(user.getRole())
                .map(SysRole::getName)
                .orElse(user.getRole());
        return UserView.from(user, roleName, permissionList(user.getRole()), includePassword);
    }

    @Transactional(readOnly = true)
    public void requireAny(FormModule firstModule, String firstAction, FormModule secondModule, String secondAction) {
        String roleCode = currentUser.entity().getRole();
        if (allowed(roleCode, firstModule, firstAction) || allowed(roleCode, secondModule, secondAction)) {
            return;
        }
        throw ApiException.forbidden("没有权限执行该操作");
    }

    @Transactional(readOnly = true)
    public void requireAny(String action, FormModule... modules) {
        String roleCode = currentUser.entity().getRole();
        if (modules != null) {
            for (FormModule module : modules) {
                if (allowed(roleCode, module, action)) {
                    return;
                }
            }
        }
        throw ApiException.forbidden("没有权限执行该操作");
    }

    @Transactional(readOnly = true)
    public boolean currentAllowed(FormModule module, String action) {
        return allowed(currentUser.entity().getRole(), module, action);
    }

    @Transactional(readOnly = true)
    public boolean currentAllowed(String module, String action) {
        return allowed(currentUser.entity().getRole(), module, action);
    }

    public boolean allowed(String roleCode, FormModule module, String action) {
        return allowed(roleCode, module.name(), action);
    }

    public boolean allowed(String roleCode, String module, String action) {
        if (ADMIN_CODE.equals(roleCode)) {
            return true;
        }
        FormPermissionView permission = permissionsFor(roleCode).get(module);
        if (permission == null) {
            return false;
        }
        return switch (action) {
            case "VIEW" -> permission.view();
            case "CREATE" -> permission.create();
            case "EDIT" -> permission.edit();
            case "DELETE" -> permission.delete();
            case "EXPORT", "IMPORT_EXPORT" -> permission.importExport();
            case "FLOW" -> permission.flow();
            case "MANAGE_VIEW" -> permission.manageView();
            default -> false;
        };
    }

    /** 任一业务表单有查看权即可做主数据联想（如填报时选客户）。 */
    @Transactional(readOnly = true)
    public void requireAnyView() {
        String roleCode = currentUser.entity().getRole();
        if (ADMIN_CODE.equals(roleCode)) {
            return;
        }
        for (FormPermissionView permission : permissionsFor(roleCode).values()) {
            if (permission.view()) {
                return;
            }
        }
        throw ApiException.forbidden("没有权限执行该操作");
    }

    @Transactional(readOnly = true)
    public void require(FormModule module, String action) {
        require(module.name(), action);
    }

    @Transactional(readOnly = true)
    public void require(String module, String action) {
        String roleCode = currentUser.entity().getRole();
        if (!allowed(roleCode, module, action)) {
            throw ApiException.forbidden("没有权限执行该操作");
        }
    }

    @Transactional(readOnly = true)
    public boolean unrestricted(FormModule module) {
        return unrestricted(module.name());
    }

    @Transactional(readOnly = true)
    public boolean unrestricted(String module) {
        SysUser user = currentUser.entity();
        if (ADMIN_CODE.equals(user.getRole())) {
            return true;
        }
        FormModule m = FormModule.fromNameOrNull(module);
        if (m != null && !m.hasDataScope()) {
            return true;
        }
        FormPermissionView permission = permissionsFor(user.getRole()).get(module);
        return permission != null && permission.view() && DataScope.from(permission.dataScope()) == DataScope.ALL;
    }

    /** 设备：仅系统管理员可看全部部门；其它角色一律按组织架构过滤。 */
    @Transactional(readOnly = true)
    public boolean adminBypassOrg() {
        return ADMIN_CODE.equals(currentUser.entity().getRole());
    }

    @Transactional(readOnly = true)
    public boolean ownOnly(FormModule module) {
        SysUser user = currentUser.entity();
        if (ADMIN_CODE.equals(user.getRole()) || !module.hasDataScope()) {
            return false;
        }
        FormPermissionView permission = permissionsFor(user.getRole()).get(module.name());
        if (permission == null || !permission.view()) {
            return false;
        }
        return DataScope.from(permission.dataScope()) == DataScope.OWN;
    }

    @Transactional(readOnly = true)
    public Long ownFilterUserId(FormModule module) {
        return ownOnly(module) ? currentUser.entity().getId() : null;
    }

    @Transactional(readOnly = true)
    public void requireOwnRecord(FormModule module, SysUser createdBy) {
        requireOwnRecord(module, createdBy == null ? null : createdBy.getId());
    }

    @Transactional(readOnly = true)
    public void requireOwnRecord(FormModule module, Long ownerId) {
        Long filter = ownFilterUserId(module);
        if (filter == null) {
            return;
        }
        if (ownerId == null || !filter.equals(ownerId)) {
            throw ApiException.forbidden("只能查看或操作个人提交的数据");
        }
    }
}
