package com.projectmgmt.oa;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.BizParse;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.security.CurrentUser;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.user.UserStatus;
import com.projectmgmt.workflow.WorkflowService;
import com.projectmgmt.workflow.dto.InboxItemView;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StaffPortalService {

    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final CurrentUser currentUser;
    private final OaService oaService;
    private final WorkflowService workflowService;

    @Transactional(readOnly = true)
    public PageResult<StaffPortalView> list(String keyword, UserStatus status, int page, int size) {
        Page<SysUser> result = userRepository.findAll(
                spec(keyword, status),
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "hireDate", "id"))
        );
        return new PageResult<>(result.map(this::toView).getContent(), result.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public List<StaffPortalView> listAll(String keyword, UserStatus status) {
        return userRepository.findAll(spec(keyword, status), Sort.by(Sort.Direction.DESC, "hireDate", "id"))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public StaffPortalView get(Long id) {
        permissionService.require(FormModule.OA_STAFF, "VIEW");
        return toView(requireVisible(id));
    }

    @Transactional(readOnly = true)
    public PageResult<OaRequestView> requests(Long id, String kindSlug, int page, int size) {
        permissionService.require(FormModule.OA_STAFF, "VIEW");
        SysUser person = requireVisible(id);
        OaKind kind = StringUtils.hasText(kindSlug) ? OaKind.fromSlug(kindSlug) : null;
        return oaService.listLinkedToUser(person, kind, page, size);
    }

    @Transactional(readOnly = true)
    public List<InboxItemView> notices(Long id, String type) {
        permissionService.require(FormModule.OA_STAFF, "VIEW");
        SysUser person = requireVisible(id);
        return workflowService.inboxFor(person.getId(), type, false);
    }

    @Transactional
    public StaffPortalView update(Long id, UpsertStaffProfile request) {
        permissionService.require(FormModule.OA_STAFF, "EDIT");
        SysUser user = requireVisible(id);
        if (request != null) {
            if (StringUtils.hasText(request.displayName())) {
                user.setDisplayName(request.displayName().trim());
            }
            user.setGender(gender(request.gender()));
            user.setIdNumber(BizParse.blankToNull(request.idNumber()));
            user.setMobile(BizParse.blankToNull(request.mobile()));
            user.setPosition(BizParse.blankToNull(request.position()));
            user.setBirthDate(StringUtils.hasText(request.birthDate())
                    ? BizParse.date(request.birthDate(), "请选择出生日期")
                    : null);
            user.setHireDate(StringUtils.hasText(request.hireDate())
                    ? BizParse.date(request.hireDate(), "请选择入职日期")
                    : null);
        }
        return toView(userRepository.save(user));
    }

    private SysUser requireVisible(Long id) {
        SysUser user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("员工不存在"));
        if (PermissionService.PUBLIC_GUEST_CODE.equals(user.getRole())) {
            throw ApiException.notFound("员工不存在");
        }
        assertVisible(user);
        return user;
    }

    private Specification<SysUser> spec(String keyword, UserStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("role"), PermissionService.PUBLIC_GUEST_CODE));
            Join<SysUser, com.projectmgmt.org.OrgDepartment> dept = root.join("department", JoinType.LEFT);
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("displayName")), like),
                        cb.like(cb.lower(root.get("mobile")), like),
                        cb.like(cb.lower(root.get("idNumber")), like),
                        cb.like(cb.lower(root.get("position")), like),
                        cb.like(cb.lower(dept.get("name")), like)
                ));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            Long ownId = permissionService.ownFilterUserId(FormModule.OA_STAFF);
            if (ownId != null) {
                SysUser me = currentUser.entity();
                Long deptId = me.getDepartment() == null ? null : me.getDepartment().getId();
                if (deptId == null) {
                    predicates.add(cb.equal(root.get("id"), ownId));
                } else {
                    predicates.add(cb.or(cb.equal(root.get("id"), ownId), cb.equal(dept.get("id"), deptId)));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void assertVisible(SysUser user) {
        Long ownId = permissionService.ownFilterUserId(FormModule.OA_STAFF);
        if (ownId == null) {
            return;
        }
        SysUser me = currentUser.entity();
        if (ownId.equals(user.getId())) {
            return;
        }
        Long myDept = me.getDepartment() == null ? null : me.getDepartment().getId();
        Long userDept = user.getDepartment() == null ? null : user.getDepartment().getId();
        if (myDept == null || userDept == null || !myDept.equals(userDept)) {
            throw ApiException.forbidden("只能查看或维护本部门人员");
        }
    }

    private StaffPortalView toView(SysUser user) {
        return new StaffPortalView(
                user.getId(),
                user.getDisplayName(),
                user.getGender(),
                genderLabel(user.getGender()),
                user.getIdNumber(),
                user.getBirthDate(),
                user.getMobile(),
                user.getPosition(),
                user.getJobLevel(),
                user.getDepartment() == null ? null : user.getDepartment().getId(),
                user.getDepartment() == null ? null : user.getDepartment().getName(),
                user.getHireDate(),
                user.getStatus(),
                user.getStatus() == UserStatus.ENABLED ? "在职" : "离职"
        );
    }

    private static String gender(String raw) {
        String value = BizParse.blankToNull(raw);
        if (value == null) {
            return null;
        }
        String upper = value.trim().toUpperCase();
        if ("MALE".equals(upper) || "男".equals(value)) {
            return "MALE";
        }
        if ("FEMALE".equals(upper) || "女".equals(value)) {
            return "FEMALE";
        }
        throw ApiException.badRequest("性别只能是男或女");
    }

    private static String genderLabel(String gender) {
        if ("MALE".equals(gender)) {
            return "男";
        }
        if ("FEMALE".equals(gender)) {
            return "女";
        }
        return gender;
    }
}
