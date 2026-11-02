package com.projectmgmt.role.dto;

import com.projectmgmt.role.DataScope;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.SysRolePermission;

public record FormPermissionView(
        String module,
        String moduleLabel,
        boolean view,
        boolean create,
        boolean edit,
        boolean delete,
        boolean importExport,
        boolean flow,
        boolean manageView,
        String dataScope,
        boolean hasDataScope,
        boolean hasCreate,
        boolean hasEdit,
        boolean hasDelete,
        boolean hasImport,
        boolean hasFlow,
        boolean hasManageView,
        boolean publicAccess,
        boolean publicDisplay,
        boolean publicSubmit,
        boolean publicQuery,
        boolean publicExport,
        boolean hasPublicAccess
) {
    public static FormPermissionView from(SysRolePermission permission) {
        FormModule module = FormModule.fromNameOrNull(permission.getModule());
        DataScope scope = permission.getDataScope() == null ? DataScope.ALL : permission.getDataScope();
        return new FormPermissionView(
                permission.getModule(),
                module == null ? permission.getModule() : module.label(),
                permission.isCanView(),
                permission.isCanCreate(),
                permission.isCanEdit(),
                permission.isCanDelete(),
                permission.isCanImportExport(),
                permission.isCanFlow(),
                permission.isCanManageView(),
                scope.name(),
                module != null && module.hasDataScope(),
                module == null || module.hasCreate(),
                module == null || module.hasEdit(),
                module == null || module.hasDelete(),
                module != null && module.hasImport(),
                module == null || module.hasFlow(),
                module == null || module.hasManageView(),
                permission.isCanPublic(),
                permission.isCanPublicDisplay(),
                permission.isCanPublicSubmit(),
                permission.isCanPublicQuery(),
                permission.isCanPublicExport(),
                module != null && module.canBePublic()
        );
    }

    public static FormPermissionView allAllowed(FormModule module) {
        return new FormPermissionView(
                module.name(),
                module.label(),
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                DataScope.ALL.name(),
                module.hasDataScope(),
                module.hasCreate(),
                module.hasEdit(),
                module.hasDelete(),
                module.hasImport(),
                module.hasFlow(),
                module.hasManageView(),
                false,
                false,
                false,
                false,
                false,
                module.canBePublic()
        );
    }

    public static FormPermissionView none(FormModule module) {
        return new FormPermissionView(
                module.name(),
                module.label(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                DataScope.ALL.name(),
                module.hasDataScope(),
                module.hasCreate(),
                module.hasEdit(),
                module.hasDelete(),
                module.hasImport(),
                module.hasFlow(),
                module.hasManageView(),
                false,
                false,
                false,
                false,
                false,
                module.canBePublic()
        );
    }
}
