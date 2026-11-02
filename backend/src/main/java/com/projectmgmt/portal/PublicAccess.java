package com.projectmgmt.portal;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.role.SysRole;
import com.projectmgmt.role.SysRolePermission;
import com.projectmgmt.role.RoleRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PublicAccess {

    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public PublicModuleFlags flags(FormModule module) {
        if (!module.canBePublic()) {
            return PublicModuleFlags.closed();
        }
        SysRole role = roleRepository.findByCode(PermissionService.PUBLIC_GUEST_CODE).orElse(null);
        if (role == null || role.getPermissions() == null) {
            return PublicModuleFlags.closed();
        }
        Map<String, SysRolePermission> stored = new LinkedHashMap<>();
        for (SysRolePermission permission : role.getPermissions()) {
            stored.put(permission.getModule(), permission);
        }
        SysRolePermission permission = stored.get(module.name());
        boolean access = permission != null && permission.isCanPublic();
        if (!access) {
            return PublicModuleFlags.closed();
        }
        return new PublicModuleFlags(
                true,
                permission.isCanPublicDisplay(),
                permission.isCanPublicSubmit(),
                permission.isCanPublicQuery(),
                permission.isCanPublicExport()
        );
    }

    public void requireListable(FormModule module) {
        PublicModuleFlags flags = flags(module);
        if (!flags.publicAccess() || (!flags.display() && !flags.query())) {
            throw ApiException.badRequest("该内容未开放公开办理");
        }
    }

    public void requireReadable(FormModule module) {
        PublicModuleFlags flags = flags(module);
        if (!flags.publicAccess() || (!flags.display() && !flags.query())) {
            throw ApiException.badRequest("该内容未开放公开办理");
        }
    }

    public void requireSubmit(FormModule module) {
        PublicModuleFlags flags = flags(module);
        if (!flags.publicAccess() || !flags.submit()) {
            throw ApiException.badRequest("该表单未开放填报");
        }
    }

    public void requireExport(FormModule module) {
        PublicModuleFlags flags = flags(module);
        if (!flags.publicAccess() || !flags.export()) {
            throw ApiException.badRequest("未开放导出");
        }
        requireListable(module);
    }
}
