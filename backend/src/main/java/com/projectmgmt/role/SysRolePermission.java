package com.projectmgmt.role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "sys_role_permission",
        uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "module"})
)
public class SysRolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id")
    private SysRole role;

    @Column(nullable = false, length = 32)
    private String module;

    @Column(name = "can_view", nullable = false)
    private boolean canView;

    @Column(name = "can_create", nullable = false)
    private boolean canCreate;

    @Column(name = "can_edit", nullable = false)
    private boolean canEdit;

    @Column(name = "can_delete", nullable = false)
    private boolean canDelete;

    @Column(name = "can_import_export", nullable = false)
    private boolean canImportExport;

    @Column(name = "can_flow", nullable = false)
    private boolean canFlow;

    @Column(name = "can_manage_view", nullable = false, columnDefinition = "boolean default false not null")
    private boolean canManageView;

    @Column(name = "can_public", nullable = false)
    private boolean canPublic;

    @Column(name = "can_public_display", nullable = false)
    private boolean canPublicDisplay;

    @Column(name = "can_public_submit", nullable = false)
    private boolean canPublicSubmit;

    @Column(name = "can_public_query", nullable = false)
    private boolean canPublicQuery;

    @Column(name = "can_public_export", nullable = false)
    private boolean canPublicExport;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_scope", nullable = false, length = 16)
    private DataScope dataScope = DataScope.ALL;

    /** 兼容既有调用方：支持按枚举设置模块。 */
    public void setModule(FormModule module) {
        this.module = module.name();
    }

    /** 轻流动态表单的模块名即表单 code，需按字符串写入。 */
    public void setModule(String module) {
        this.module = module;
    }
}
