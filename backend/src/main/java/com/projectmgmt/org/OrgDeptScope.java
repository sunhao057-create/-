package com.projectmgmt.org;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.security.CurrentUser;
import com.projectmgmt.user.SysUser;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrgDeptScope {

    private final OrgDepartmentRepository departmentRepository;
    private final CurrentUser currentUser;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public boolean adminBypass() {
        return permissionService.adminBypassOrg();
    }

    /**
     * 可见部门：本人所在部门及其全部下级。管理员返回 null 表示不限制。
     * 未分配部门的非管理员返回空集合（看不到任何部门设备）。
     */
    @Transactional(readOnly = true)
    public Set<Long> visibleDepartmentIds() {
        if (adminBypass()) {
            return null;
        }
        SysUser user = currentUser.entity();
        if (user.getDepartment() == null || user.getDepartment().getId() == null) {
            return Set.of();
        }
        return descendantsIncluding(user.getDepartment().getId());
    }

    /**
     * 本公司：从本人部门向上找到最上级部门，再包含其全部下级。
     * 管理员返回 null。未分配部门返回空集合。
     */
    @Transactional(readOnly = true)
    public Set<Long> companyDepartmentIds() {
        if (adminBypass()) {
            return null;
        }
        SysUser user = currentUser.entity();
        if (user.getDepartment() == null || user.getDepartment().getId() == null) {
            return Set.of();
        }
        List<OrgDepartment> all = departmentRepository.findAllByOrderBySortOrderAscIdAsc();
        Long root = rootId(all, user.getDepartment().getId());
        if (root == null) {
            return Set.of();
        }
        return descendantsIncluding(root);
    }

    @Transactional(readOnly = true)
    public String companyName() {
        if (adminBypass()) {
            return "全部公司";
        }
        SysUser user = currentUser.entity();
        if (user.getDepartment() == null || user.getDepartment().getId() == null) {
            return "";
        }
        List<OrgDepartment> all = departmentRepository.findAllByOrderBySortOrderAscIdAsc();
        Long root = rootId(all, user.getDepartment().getId());
        if (root == null) {
            return "";
        }
        for (OrgDepartment dept : all) {
            if (root.equals(dept.getId())) {
                return dept.getName();
            }
        }
        return "";
    }

    @Transactional(readOnly = true)
    public void assertInCompany(OrgDepartment department) {
        if (adminBypass()) {
            return;
        }
        if (department == null || department.getId() == null) {
            throw ApiException.forbidden("只能查看本公司的设备");
        }
        Set<Long> ids = companyDepartmentIds();
        if (ids == null || !ids.contains(department.getId())) {
            throw ApiException.forbidden("只能查看本公司的设备");
        }
    }

    private Long rootId(List<OrgDepartment> all, Long deptId) {
        Map<Long, Long> parentOf = new HashMap<>();
        for (OrgDepartment dept : all) {
            parentOf.put(dept.getId(), dept.getParent() == null ? null : dept.getParent().getId());
        }
        Long current = deptId;
        Set<Long> guard = new HashSet<>();
        while (current != null && parentOf.get(current) != null && guard.add(current)) {
            current = parentOf.get(current);
        }
        return current;
    }

    @Transactional(readOnly = true)
    public Set<Long> descendantsIncluding(Long rootId) {
        List<OrgDepartment> all = departmentRepository.findAllByOrderBySortOrderAscIdAsc();
        Set<Long> ids = new HashSet<>();
        collect(all, rootId, ids);
        ids.add(rootId);
        return ids;
    }

    public <T> Specification<T> departmentIn(String departmentPath) {
        Set<Long> ids = visibleDepartmentIds();
        if (ids == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        if (ids.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> {
            var path = root.get(departmentPath).get("id");
            return path.in(ids);
        };
    }

    public <T> Specification<T> deviceDepartmentIn() {
        Set<Long> ids = visibleDepartmentIds();
        if (ids == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        if (ids.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> {
            var path = root.get("device").get("department").get("id");
            return path.in(ids);
        };
    }

    @Transactional(readOnly = true)
    public void assertVisible(OrgDepartment department) {
        if (adminBypass()) {
            return;
        }
        if (department == null || department.getId() == null) {
            throw ApiException.forbidden("没有权限查看该部门数据");
        }
        Set<Long> ids = visibleDepartmentIds();
        if (ids == null) {
            return;
        }
        if (!ids.contains(department.getId())) {
            throw ApiException.forbidden("只能查看本部门及下级部门的数据");
        }
    }

    private void collect(List<OrgDepartment> all, Long parentId, Set<Long> ids) {
        for (OrgDepartment dept : all) {
            if (dept.getParent() != null && parentId.equals(dept.getParent().getId()) && ids.add(dept.getId())) {
                collect(all, dept.getId(), ids);
            }
        }
    }
}
