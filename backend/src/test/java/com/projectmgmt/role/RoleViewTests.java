package com.projectmgmt.role;

import static org.assertj.core.api.Assertions.assertThat;

import com.projectmgmt.role.dto.FormPermissionView;
import com.projectmgmt.role.dto.RoleView;
import org.junit.jupiter.api.Test;

class RoleViewTests {

    @Test
    void adminViewAlwaysIncludesBrandingAllAllowed() {
        SysRole role = new SysRole();
        role.setCode("ADMIN");
        role.setName("系统管理员");
        role.setBuiltin(true);

        RoleView view = RoleView.from(role);

        FormPermissionView branding = view.permissions().stream()
                .filter(item -> "BRANDING".equals(item.module()))
                .findFirst()
                .orElseThrow();
        assertThat(branding.view()).isTrue();
        assertThat(branding.create()).isTrue();
        assertThat(branding.edit()).isTrue();
        assertThat(branding.delete()).isTrue();
        assertThat(branding.importExport()).isTrue();
        assertThat(branding.dataScope()).isEqualTo("ALL");
        FormPermissionView theme = view.permissions().stream()
                .filter(item -> "THEME".equals(item.module()))
                .findFirst()
                .orElseThrow();
        assertThat(theme.view()).isTrue();
        assertThat(theme.hasCreate()).isFalse();
        FormPermissionView projectStats = view.permissions().stream()
                .filter(item -> "PROJECT_ANALYTICS".equals(item.module()))
                .findFirst()
                .orElseThrow();
        assertThat(projectStats.view()).isTrue();
        assertThat(projectStats.hasCreate()).isFalse();
        assertThat(projectStats.hasEdit()).isFalse();
        assertThat(projectStats.hasDelete()).isFalse();
        FormPermissionView purchaseStats = view.permissions().stream()
                .filter(item -> "PURCHASE_ANALYTICS".equals(item.module()))
                .findFirst()
                .orElseThrow();
        assertThat(purchaseStats.hasCreate()).isFalse();
        assertThat(purchaseStats.hasEdit()).isFalse();
        assertThat(view.permissions()).hasSize(FormModule.all().size());
    }
}
