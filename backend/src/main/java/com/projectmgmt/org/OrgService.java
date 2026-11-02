package com.projectmgmt.org;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.device.DeviceRepository;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.user.UserStatus;
import com.projectmgmt.workflow.WorkflowService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrgService {

    private final OrgDepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final WorkflowService workflowService;
    private final DeviceRepository deviceRepository;

    public OrgService(
            OrgDepartmentRepository departmentRepository,
            UserRepository userRepository,
            @Lazy WorkflowService workflowService,
            DeviceRepository deviceRepository
    ) {
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.workflowService = workflowService;
        this.deviceRepository = deviceRepository;
    }

    @Transactional(readOnly = true)
    public List<OrgDepartmentView> list() {
        return departmentRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrgMemberView> members(Long departmentId) {
        requireDept(departmentId);
        return userRepository.findByDepartment_IdOrderByDisplayNameAsc(departmentId).stream()
                .map(user -> new OrgMemberView(
                        user.getId(),
                        user.getUsername(),
                        user.getDisplayName(),
                        user.isDeptLeader(),
                        user.getStatus() == UserStatus.ENABLED ? "ENABLED" : "DISABLED"
                ))
                .toList();
    }

    @Transactional
    public OrgDepartmentView create(UpsertDepartmentRequest request) {
        OrgDepartment dept = new OrgDepartment();
        apply(dept, request, null);
        return toView(departmentRepository.save(dept));
    }

    @Transactional
    public OrgDepartmentView update(Long id, UpsertDepartmentRequest request) {
        OrgDepartment dept = requireDept(id);
        apply(dept, request, id);
        OrgDepartmentView view = toView(departmentRepository.save(dept));
        workflowService.reassignPendingOrgTasks();
        return view;
    }

    @Transactional
    public void delete(Long id) {
        OrgDepartment dept = requireDept(id);
        if (departmentRepository.existsByParent_Id(id)) {
            throw ApiException.badRequest("请先删除下级部门");
        }
        if (userRepository.existsByDepartment_Id(id)) {
            throw ApiException.badRequest("请先把该部门人员调整到其他部门");
        }
        if (deviceRepository.existsByDepartment_Id(id)) {
            throw ApiException.badRequest("请先调整该部门下的设备");
        }
        departmentRepository.delete(dept);
    }

    @Transactional
    public List<OrgMemberView> assignMember(Long departmentId, AssignMemberRequest request) {
        OrgDepartment dept = requireDept(departmentId);
        List<Long> ids = request == null ? List.of() : request.ids();
        if (ids.isEmpty()) {
            throw ApiException.badRequest("请选择人员");
        }
        boolean leader = request != null && Boolean.TRUE.equals(request.deptLeader());
        for (Long userId : ids) {
            SysUser user = userRepository.findById(userId)
                    .orElseThrow(() -> ApiException.badRequest("用户不存在"));
            user.setDepartment(dept);
            user.setDeptLeader(leader);
        }
        workflowService.reassignPendingOrgTasks();
        return members(departmentId);
    }

    @Transactional
    public List<OrgMemberView> removeMember(Long departmentId, Long userId) {
        requireDept(departmentId);
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.badRequest("用户不存在"));
        if (user.getDepartment() == null || !departmentId.equals(user.getDepartment().getId())) {
            throw ApiException.badRequest("该人员不属于此部门");
        }
        user.setDepartment(null);
        user.setDeptLeader(false);
        workflowService.reassignPendingOrgTasks();
        return members(departmentId);
    }

    @Transactional
    public void applyUserDepartment(SysUser user, Long departmentId, Boolean deptLeader) {
        if (departmentId == null) {
            user.setDepartment(null);
            user.setDeptLeader(false);
            return;
        }
        OrgDepartment dept = requireDept(departmentId);
        user.setDepartment(dept);
        user.setDeptLeader(Boolean.TRUE.equals(deptLeader));
    }

    private void apply(OrgDepartment dept, UpsertDepartmentRequest request, Long selfId) {
        String name = request.name() == null ? "" : request.name().trim();
        if (!StringUtils.hasText(name)) {
            throw ApiException.badRequest("请填写部门名称");
        }
        dept.setName(name);
        dept.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        if (request.parentId() == null) {
            dept.setParent(null);
            return;
        }
        if (selfId != null && selfId.equals(request.parentId())) {
            throw ApiException.badRequest("上级部门不能是自己");
        }
        OrgDepartment parent = requireDept(request.parentId());
        if (selfId != null && wouldCycle(selfId, parent)) {
            throw ApiException.badRequest("上级部门不能形成循环");
        }
        dept.setParent(parent);
    }

    private boolean wouldCycle(Long selfId, OrgDepartment parent) {
        Set<Long> seen = new HashSet<>();
        OrgDepartment current = parent;
        int depth = 0;
        while (current != null && depth++ < 20 && seen.add(current.getId())) {
            if (selfId.equals(current.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    public OrgDepartment require(Long id) {
        if (id == null) {
            throw ApiException.badRequest("请选择部门");
        }
        return departmentRepository.findById(id).orElseThrow(() -> ApiException.notFound("部门不存在"));
    }

    private OrgDepartment requireDept(Long id) {
        return require(id);
    }

    private OrgDepartmentView toView(OrgDepartment dept) {
        OrgDepartment parent = dept.getParent();
        long members = userRepository.findByDepartment_IdOrderByDisplayNameAsc(dept.getId()).size();
        long leaders = userRepository.findByDepartment_IdAndDeptLeaderTrueAndStatus(dept.getId(), UserStatus.ENABLED).size();
        return new OrgDepartmentView(
                dept.getId(),
                dept.getName(),
                parent == null ? null : parent.getId(),
                parent == null ? null : parent.getName(),
                dept.getSortOrder(),
                (int) members,
                (int) leaders
        );
    }
}
