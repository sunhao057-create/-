package com.projectmgmt.formview;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.security.CurrentUser;
import com.projectmgmt.user.SysUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class FormViewService {

    private static final Set<String> TYPES = Set.of("TABLE", "CARD", "BOARD", "GANTT", "TREE", "CALENDAR");
    private static final Set<String> VISIBILITY = Set.of("ALL", "ADMIN", "SELF");
    private static final Set<String> RANGES = Set.of("ALL", "MINE", "STARTED", "DONE", "RELATED");
    private static final Set<String> HEIGHTS = Set.of("COMPACT", "MEDIUM", "TALL");

    private final FormViewRepository repository;
    private final PermissionService permissionService;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Transactional(readOnly = true)
    public List<FormViewDto> list(String module) {
        FormModule formModule = requireModule(module);
        permissionService.require(formModule, "VIEW");
        SysUser user = currentUser.entity();
        boolean admin = PermissionService.ADMIN_CODE.equals(user.getRole());
        boolean canManage = permissionService.currentAllowed(formModule.name(), "MANAGE_VIEW");
        List<FormViewDto> result = new ArrayList<>();
        if (admin) {
            result.add(builtin(formModule.name()));
        }
        for (FormView item : repository.findByModuleOrderByPinTopDescIdAsc(formModule.name())) {
            if (visibleTo(item, user.getId(), admin, canManage)) {
                result.add(toDto(item, false));
            }
        }
        return result;
    }

    @Transactional
    public FormViewDto create(String module, UpsertFormViewRequest request) {
        FormModule formModule = requireModule(module);
        permissionService.require(formModule, "MANAGE_VIEW");
        FormView item = new FormView();
        item.setModule(formModule.name());
        SysUser user = currentUser.entity();
        item.setCreatedById(user.getId());
        item.setCreatedByName(user.getDisplayName());
        apply(item, request);
        return toDto(repository.save(item), false);
    }

    @Transactional
    public FormViewDto update(String module, Long id, UpsertFormViewRequest request) {
        FormModule formModule = requireModule(module);
        permissionService.require(formModule, "MANAGE_VIEW");
        FormView item = requireOwned(formModule.name(), id);
        apply(item, request);
        return toDto(item, false);
    }

    @Transactional
    public void delete(String module, Long id) {
        FormModule formModule = requireModule(module);
        permissionService.require(formModule, "MANAGE_VIEW");
        repository.delete(requireOwned(formModule.name(), id));
    }

    private FormView requireOwned(String module, Long id) {
        FormView item = repository.findById(id).orElseThrow(() -> ApiException.notFound("视图不存在"));
        if (!module.equals(item.getModule())) {
            throw ApiException.notFound("视图不存在");
        }
        return item;
    }

    private void apply(FormView item, UpsertFormViewRequest request) {
        item.setName(request.name().trim());
        item.setType(normalize(request.type(), TYPES, "TABLE", "不支持的视图类型"));
        item.setRemark(request.remark() == null ? "" : request.remark().trim());
        item.setPinTop(Boolean.TRUE.equals(request.pinTop()));
        item.setVisibility(normalize(request.visibility(), VISIBILITY, "ALL", "可见范围无效"));
        item.setDataRange(normalize(request.dataRange(), RANGES, "ALL", "数据范围无效"));
        item.setRowHeight(normalize(request.rowHeight(), HEIGHTS, "MEDIUM", "行高无效"));
        item.setSortField(blankToNull(request.sortField()));
        item.setSortDir("ASC".equalsIgnoreCase(request.sortDir()) ? "ASC" : "DESC");
        item.setSearchEnabled(request.searchEnabled() == null || request.searchEnabled());
        item.setQueryEnabled(request.queryEnabled() == null || request.queryEnabled());
        item.setFieldsJson(writeJson(request.fields() == null ? List.of() : request.fields()));
        item.setConfigJson(writeJson(request.extra() == null ? Map.of() : request.extra()));
    }

    private boolean visibleTo(FormView item, Long userId, boolean admin, boolean canManage) {
        String visibility = item.getVisibility() == null ? "ALL" : item.getVisibility();
        return switch (visibility) {
            case "ADMIN" -> admin || canManage;
            case "SELF" -> admin || userId.equals(item.getCreatedById());
            default -> true;
        };
    }

    private FormViewDto builtin(String module) {
        return new FormViewDto(
                0L,
                module,
                "全部数据",
                "TABLE",
                "",
                false,
                "ADMIN",
                "ALL",
                "MEDIUM",
                null,
                "DESC",
                true,
                true,
                List.of(),
                Map.of(),
                true,
                null,
                null
        );
    }

    @SuppressWarnings("unchecked")
    private FormViewDto toDto(FormView item, boolean builtin) {
        List<String> fields = readList(item.getFieldsJson());
        Map<String, Object> extra = readMap(item.getConfigJson());
        return new FormViewDto(
                item.getId(),
                item.getModule(),
                item.getName(),
                item.getType(),
                item.getRemark(),
                item.isPinTop(),
                item.getVisibility(),
                item.getDataRange(),
                item.getRowHeight(),
                item.getSortField(),
                item.getSortDir(),
                item.isSearchEnabled(),
                item.isQueryEnabled(),
                fields,
                extra,
                builtin,
                item.getCreatedById(),
                item.getCreatedByName()
        );
    }

    private FormModule requireModule(String module) {
        FormModule formModule = FormModule.fromNameOrNull(module);
        if (formModule == null || !formModule.hasManageView()) {
            throw ApiException.badRequest("该表单不支持数据视图");
        }
        return formModule;
    }

    private String normalize(String value, Set<String> allowed, String fallback, String message) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String next = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(next)) {
            throw ApiException.badRequest(message);
        }
        return next;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> readList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<?> raw = objectMapper.readValue(json, List.class);
            List<String> result = new ArrayList<>();
            for (Object item : raw) {
                if (item != null) result.add(String.valueOf(item));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }
}
