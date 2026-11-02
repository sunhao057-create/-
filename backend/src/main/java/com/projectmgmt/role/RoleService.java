package com.projectmgmt.role;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.dynamic.FormDefinition;
import com.projectmgmt.dynamic.FormDefinitionRepository;
import com.projectmgmt.role.dto.FormPermissionRequest;
import com.projectmgmt.role.dto.RoleView;
import com.projectmgmt.role.dto.UpsertRoleRequest;
import com.projectmgmt.user.UserRepository;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RoleService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Set<String> RESERVED_CODES = Set.of(
            PermissionService.ADMIN_CODE,
            PermissionService.USER_CODE,
            PermissionService.PUBLIC_GUEST_CODE
    );
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final FormDefinitionRepository formDefinitionRepository;

    @Transactional(readOnly = true)
    public List<RoleView> list() {
        return roleRepository.findAll().stream().map(RoleView::from).toList();
    }

    @Transactional(readOnly = true)
    public RoleView get(Long id) {
        return RoleView.from(require(id));
    }

    @Transactional
    public RoleView create(UpsertRoleRequest request) {
        SysRole role = new SysRole();
        role.setCode(nextUniqueCode());
        role.setName(request.name().trim());
        role.setDescription(blankToNull(request.description()));
        role.setBuiltin(false);
        replacePermissions(role, request.permissions());
        return RoleView.from(roleRepository.save(role));
    }

    @Transactional
    public RoleView update(Long id, UpsertRoleRequest request) {
        SysRole role = require(id);
        role.setName(request.name().trim());
        role.setDescription(blankToNull(request.description()));
        replacePermissions(role, request.permissions());
        return RoleView.from(role);
    }

    @Transactional
    public void delete(Long id) {
        SysRole role = require(id);
        if (role.isBuiltin()) {
            throw ApiException.badRequest("内置角色不能删除");
        }
        if (userRepository.countByRole(role.getCode()) > 0) {
            throw ApiException.badRequest("仍有账号使用该角色，不能删除");
        }
        roleRepository.delete(role);
    }

    public SysRole requireByCode(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        return roleRepository.findByCode(normalized)
                .orElseThrow(() -> ApiException.badRequest("角色不存在"));
    }

    public SysRole requireByNameOrCode(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.isBlank()) {
            throw ApiException.badRequest("请填写角色名称");
        }
        return roleRepository.findFirstByName(raw)
                .or(() -> roleRepository.findByCode(raw.toUpperCase(Locale.ROOT)))
                .orElseThrow(() -> ApiException.badRequest("角色不存在，请填写角色管理中的角色名称"));
    }

    @Transactional
    public void ensureBuiltin() {
        ensureRole(PermissionService.ADMIN_CODE, "系统管理员", "拥有全部权限", module -> true);
        ensureRole(PermissionService.USER_CODE, "普通用户", "可查看和编辑项目立项、客户、合同、供应商、采购、财务、仓库、流程，可查看组织架构与统计分析", module ->
                module == FormModule.PROJECT
                        || module == FormModule.DEPOSIT_APPLY || module == FormModule.DEPOSIT_RETURN
                        || module == FormModule.BIDDING
                        || module == FormModule.CUSTOMER
                        || module == FormModule.CONTRACT || module == FormModule.SUPPLIER || module == FormModule.WORKFLOW || module == FormModule.ORG
                        || module == FormModule.BANK_ACCOUNT || module == FormModule.EXPENSE
                        || module == FormModule.PURCHASE || module == FormModule.PURCHASE_PAY
                        || module == FormModule.WAREHOUSE || module == FormModule.STOCK_OUT_PURCHASE || module == FormModule.STOCK_OUT_PROJECT
                        || module == FormModule.STOCK_TRANSFER
                        || module == FormModule.DEVICE || module == FormModule.DEVICE_REPAIR || module == FormModule.DEVICE_MOVE
                        || module == FormModule.DEVICE_SCRAP || module == FormModule.DEVICE_MAINT
                        || module.isOa()
                        || module.isTech()
                        || module.isAnalytics());
        ensureRole(PermissionService.PUBLIC_GUEST_CODE, "公开访客", "未登录访客的公开办理权限", module -> false);
        syncMissingPermissions();
        syncDynamicFormPermissions();
    }

    /**
     * 轻流动态表单：为内置角色补齐按表单 code 的权限行（幂等）。
     * 已存在的行不覆盖，保留角色管理里已授予的开关。
     */
    private void syncDynamicFormPermissions() {
        List<String> codes = formDefinitionRepository.findAll().stream()
                .map(FormDefinition::getCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            return;
        }
        for (String roleCode : List.of(
                PermissionService.ADMIN_CODE,
                PermissionService.USER_CODE,
                PermissionService.PUBLIC_GUEST_CODE)) {
            SysRole role = roleRepository.findByCode(roleCode).orElse(null);
            if (role == null) {
                continue;
            }
            java.util.Set<String> existing = role.getPermissions().stream()
                    .map(SysRolePermission::getModule)
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
            boolean admin = PermissionService.ADMIN_CODE.equals(roleCode);
            boolean user = PermissionService.USER_CODE.equals(roleCode);
            boolean changed = false;
            for (String code : codes) {
                if (existing.contains(code)) {
                    continue;
                }
                SysRolePermission permission = new SysRolePermission();
                permission.setRole(role);
                permission.setModule(code);
                permission.setCanView(admin || user);
                permission.setCanCreate(admin);
                permission.setCanEdit(admin);
                permission.setCanDelete(admin);
                permission.setCanImportExport(admin);
                permission.setDataScope(DataScope.ALL);
                role.getPermissions().add(permission);
                existing.add(code);
                changed = true;
            }
            if (changed) {
                roleRepository.save(role);
            }
        }
    }

    private void syncMissingPermissions() {
        for (SysRole role : roleRepository.findAll()) {
            java.util.Set<String> existing = role.getPermissions().stream()
                    .map(SysRolePermission::getModule)
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
            boolean changed = false;
            boolean admin = PermissionService.ADMIN_CODE.equals(role.getCode());
            boolean builtinUser = PermissionService.USER_CODE.equals(role.getCode());
            if (admin) {
                for (SysRolePermission permission : role.getPermissions()) {
                    if (!permission.isCanView() || !permission.isCanCreate() || !permission.isCanEdit()
                            || !permission.isCanDelete() || !permission.isCanImportExport()
                            || !permission.isCanFlow() || !permission.isCanManageView()
                            || permission.getDataScope() != DataScope.ALL) {
                        permission.setCanView(true);
                        permission.setCanCreate(true);
                        permission.setCanEdit(true);
                        permission.setCanDelete(true);
                        permission.setCanImportExport(true);
                        permission.setCanFlow(true);
                        permission.setCanManageView(true);
                        permission.setDataScope(DataScope.ALL);
                        changed = true;
                    }
                }
            }
            if (builtinUser) {
                for (SysRolePermission permission : role.getPermissions()) {
                    if (permission.getDataScope() == null) {
                        permission.setDataScope(DataScope.ALL);
                        changed = true;
                    }
                }
            }
            for (FormModule module : FormModule.all()) {
                if (existing.contains(module.name())) {
                    continue;
                }
                boolean visible = admin || (PermissionService.USER_CODE.equals(role.getCode())
                        && builtinUserVisible(module));
                if (!visible) {
                    visible = inheritedAnalyticsView(role, module);
                }
                boolean writable = admin || (PermissionService.USER_CODE.equals(role.getCode())
                        && builtinUserWritable(module));
                SysRolePermission permission = new SysRolePermission();
                permission.setRole(role);
                permission.setModule(module);
                permission.setCanView(visible);
                permission.setCanCreate(writable && module.hasCreate());
                permission.setCanEdit(writable && module.hasEdit());
                permission.setCanDelete(admin && module.hasDelete());
                permission.setCanImportExport(admin && module.hasImport());
                permission.setCanFlow(admin && module.hasFlow());
                permission.setCanManageView(admin && module.hasManageView());
                permission.setDataScope(module.hasDataScope() && visible
                        ? inheritedAnalyticsScope(role, module)
                        : DataScope.ALL);
                role.getPermissions().add(permission);
                changed = true;
            }
            if (changed) {
                roleRepository.save(role);
            }
        }
    }

    private SysRole ensureRole(
            String code,
            String name,
            String description,
            java.util.function.Predicate<FormModule> editableModules
    ) {
        return roleRepository.findByCode(code).orElseGet(() -> {
            SysRole role = new SysRole();
            role.setCode(code);
            role.setName(name);
            role.setDescription(description);
            role.setBuiltin(true);
            for (FormModule module : FormModule.all()) {
                boolean visible = editableModules.test(module);
                boolean writable = visible && builtinUserWritable(code, module);
                SysRolePermission permission = new SysRolePermission();
                permission.setRole(role);
                permission.setModule(module);
                permission.setCanView(visible);
                permission.setCanCreate(writable && module.hasCreate());
                permission.setCanEdit(writable && module.hasEdit());
                permission.setCanDelete(PermissionService.ADMIN_CODE.equals(code) && visible && module.hasDelete());
                permission.setCanImportExport(PermissionService.ADMIN_CODE.equals(code) && visible && module.hasImport());
                permission.setCanFlow(PermissionService.ADMIN_CODE.equals(code) && visible && module.hasFlow());
                permission.setCanManageView(PermissionService.ADMIN_CODE.equals(code) && visible && module.hasManageView());
                permission.setDataScope(DataScope.ALL);
                role.getPermissions().add(permission);
            }
            return roleRepository.save(role);
        });
    }

    private SysRole require(Long id) {
        return roleRepository.findById(id).orElseThrow(() -> ApiException.notFound("角色不存在"));
    }

    private void replacePermissions(SysRole role, List<FormPermissionRequest> requests) {
        java.util.Map<String, SysRolePermission> current = new java.util.LinkedHashMap<>();
        for (SysRolePermission permission : role.getPermissions()) {
            current.put(permission.getModule(), permission);
        }
        for (FormModule module : FormModule.all()) {
            FormPermissionRequest item = requests == null ? null : requests.stream()
                    .filter(req -> module.name().equalsIgnoreCase(req.module()))
                    .findFirst()
                    .orElse(null);
            SysRolePermission permission = current.remove(module.name());
            if (permission == null) {
                permission = new SysRolePermission();
                permission.setRole(role);
                permission.setModule(module);
                role.getPermissions().add(permission);
            }
            if (item == null) {
                permission.setCanView(false);
                permission.setCanCreate(false);
                permission.setCanEdit(false);
                permission.setCanDelete(false);
                permission.setCanImportExport(false);
                permission.setCanFlow(false);
                permission.setCanManageView(false);
                permission.setDataScope(DataScope.ALL);
            } else {
                boolean create = module.hasCreate() && item.createEnabled();
                boolean edit = module.hasEdit() && item.editEnabled();
                boolean delete = module.hasDelete() && item.deleteEnabled();
                boolean importExport = module.hasImport() && item.importExportEnabled();
                boolean flow = module.hasFlow() && item.flowEnabled();
                boolean manageView = module.hasManageView() && item.manageViewEnabled();
                boolean view = item.viewEnabled() || create || edit || delete || importExport || flow || manageView;
                permission.setCanView(view);
                permission.setCanCreate(create);
                permission.setCanEdit(edit);
                permission.setCanDelete(delete);
                permission.setCanImportExport(importExport);
                permission.setCanFlow(flow);
                permission.setCanManageView(manageView);
                permission.setDataScope(module.hasDataScope() && view ? DataScope.from(item.dataScope()) : DataScope.ALL);
            }
            if (PermissionService.PUBLIC_GUEST_CODE.equals(role.getCode())) {
                permission.setCanView(false);
                permission.setCanCreate(false);
                permission.setCanEdit(false);
                permission.setCanDelete(false);
                permission.setCanImportExport(false);
                permission.setCanFlow(false);
                permission.setCanManageView(false);
                permission.setDataScope(DataScope.ALL);
            }
            applyPublicFlags(role, module, permission, item);
            if (PermissionService.ADMIN_CODE.equals(role.getCode())) {
                permission.setCanView(true);
                permission.setCanCreate(true);
                permission.setCanEdit(true);
                permission.setCanDelete(true);
                permission.setCanImportExport(true);
                permission.setCanFlow(true);
                permission.setCanManageView(true);
                permission.setDataScope(DataScope.ALL);
                permission.setCanPublic(false);
                permission.setCanPublicDisplay(false);
                permission.setCanPublicSubmit(false);
                permission.setCanPublicQuery(false);
                permission.setCanPublicExport(false);
            }
        }
        role.getPermissions().removeIf(permission -> current.containsKey(permission.getModule()));
    }

    private void applyPublicFlags(
            SysRole role,
            FormModule module,
            SysRolePermission permission,
            FormPermissionRequest item
    ) {
        boolean guest = PermissionService.PUBLIC_GUEST_CODE.equals(role.getCode()) && module.canBePublic();
        boolean access = guest && item != null && item.publicAccessEnabled();
        permission.setCanPublic(access);
        permission.setCanPublicDisplay(access && item != null && item.publicDisplayEnabled());
        permission.setCanPublicSubmit(access && item != null && item.publicSubmitEnabled());
        permission.setCanPublicQuery(access && item != null && item.publicQueryEnabled());
        permission.setCanPublicExport(access && item != null && item.publicExportEnabled());
    }

    private String nextUniqueCode() {
        for (int i = 0; i < 32; i++) {
            StringBuilder builder = new StringBuilder("R");
            for (int n = 0; n < 8; n++) {
                builder.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String code = builder.toString();
            if (!RESERVED_CODES.contains(code) && !roleRepository.existsByCode(code)) {
                return code;
            }
        }
        throw ApiException.badRequest("角色编码生成失败，请重试");
    }

    private static boolean builtinUserVisible(FormModule module) {
        return module == FormModule.PROJECT
                || module == FormModule.DEPOSIT_APPLY || module == FormModule.DEPOSIT_RETURN
                || module == FormModule.BIDDING
                || module == FormModule.CUSTOMER || module == FormModule.CONTRACT || module == FormModule.SUPPLIER
                || module == FormModule.WORKFLOW || module == FormModule.ORG
                || module == FormModule.BANK_ACCOUNT || module == FormModule.EXPENSE
                || module == FormModule.PURCHASE || module == FormModule.PURCHASE_PAY
                || module == FormModule.WAREHOUSE || module == FormModule.STOCK_OUT_PURCHASE || module == FormModule.STOCK_OUT_PROJECT
                || module == FormModule.STOCK_TRANSFER
                || module == FormModule.DEVICE || module == FormModule.DEVICE_REPAIR || module == FormModule.DEVICE_MOVE
                || module == FormModule.DEVICE_SCRAP || module == FormModule.DEVICE_MAINT
                || module.isOa()
                || module.isTech()
                || module.isAnalytics();
    }

    private static boolean inheritedAnalyticsView(SysRole role, FormModule module) {
        FormModule source = module.analyticsSource();
        if (source == null || role.getPermissions() == null) {
            return false;
        }
        return role.getPermissions().stream()
                .anyMatch(item -> source.name().equals(item.getModule()) && item.isCanView());
    }

    private static DataScope inheritedAnalyticsScope(SysRole role, FormModule module) {
        FormModule source = module.analyticsSource();
        if (source == null || role.getPermissions() == null) {
            return DataScope.ALL;
        }
        return role.getPermissions().stream()
                .filter(item -> source.name().equals(item.getModule()))
                .map(item -> item.getDataScope() == null ? DataScope.ALL : item.getDataScope())
                .findFirst()
                .orElse(DataScope.ALL);
    }

    private static boolean builtinUserWritable(FormModule module) {
        return builtinUserVisible(module) && module != FormModule.ORG;
    }

    private static boolean builtinUserWritable(String roleCode, FormModule module) {
        if (PermissionService.USER_CODE.equals(roleCode)) {
            return builtinUserWritable(module);
        }
        return true;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
