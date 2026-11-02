package com.projectmgmt.oa;

import com.projectmgmt.auth.AuthService;
import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.BizParse;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.customer.Customer;
import com.projectmgmt.org.OrgDepartment;
import com.projectmgmt.org.OrgDepartmentView;
import com.projectmgmt.org.OrgService;
import com.projectmgmt.profile.FileStorageService;
import com.projectmgmt.project.AttachmentView;
import com.projectmgmt.project.Project;
import com.projectmgmt.role.DataScopeQuery;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.user.StaffView;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.user.UserStatus;
import com.projectmgmt.workflow.WorkflowInstance;
import com.projectmgmt.workflow.WorkflowInstanceRepository;
import com.projectmgmt.workflow.WorkflowService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class OaService {

    private final OaRequestRepository requestRepository;
    private final OrgService orgService;
    private final UserRepository userRepository;
    private final WorkflowService workflowService;
    private final WorkflowInstanceRepository instanceRepository;
    private final PermissionService permissionService;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;
    private final OaLoanFinanceService loanFinance;

    @Transactional(readOnly = true)
    public OaLookupsView lookups() {
        List<OrgDepartmentView> departments = orgService.list();
        List<StaffView> staff = userRepository.findByStatusOrderByDisplayNameAsc(UserStatus.ENABLED).stream()
                .map(StaffView::from)
                .toList();
        return new OaLookupsView(departments, staff);
    }

    @Transactional(readOnly = true)
    public PageResult<OaRequestView> list(OaKind kind, String keyword, int page, int size) {
        FormModule module = kind.module();
        Specification<OaRequest> spec = (root, query, cb) -> cb.equal(root.get("kind"), kind);
        spec = spec.and(DataScopeQuery.createdBy(permissionService.ownFilterUserId(module)));
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> {
                if (query != null) {
                    query.distinct(true);
                }
                Join<OaRequest, OrgDepartment> dept = root.join("department", JoinType.LEFT);
                Join<OaRequest, OrgDepartment> target = root.join("targetDepartment", JoinType.LEFT);
                List<Predicate> ors = new ArrayList<>();
                ors.add(cb.like(cb.lower(root.get("code")), like));
                ors.add(cb.like(cb.lower(root.get("applicantName")), like));
                ors.add(cb.like(cb.lower(root.get("reason")), like));
                ors.add(cb.like(cb.lower(root.get("position")), like));
                ors.add(cb.like(cb.lower(root.get("targetPosition")), like));
                ors.add(cb.like(cb.lower(cb.coalesce(dept.get("name"), "")), like));
                ors.add(cb.like(cb.lower(cb.coalesce(target.get("name"), "")), like));
                return cb.or(ors.toArray(Predicate[]::new));
            });
        }
        Page<OaRequest> result = requestRepository.findAll(
                spec,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id"))
        );
        return new PageResult<>(result.map(this::toView).getContent(), result.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public PageResult<OaRequestView> listLinkedToUser(SysUser person, OaKind kind, int page, int size) {
        List<OaKind> kinds = new ArrayList<>();
        if (kind != null) {
            permissionService.require(kind.module(), "VIEW");
            kinds.add(kind);
        } else {
            for (OaKind item : OaKind.values()) {
                if (permissionService.currentAllowed(item.module(), "VIEW")) {
                    kinds.add(item);
                }
            }
        }
        if (kinds.isEmpty()) {
            return new PageResult<>(List.of(), 0, page, size);
        }
        Long personId = person.getId();
        String idNumber = person.getIdNumber();
        String mobile = person.getMobile();
        Specification<OaRequest> spec = (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            Join<OaRequest, SysUser> staff = root.join("staff", JoinType.LEFT);
            Join<OaRequest, SysUser> creator = root.join("createdBy", JoinType.LEFT);
            List<Predicate> linked = new ArrayList<>();
            linked.add(cb.equal(staff.get("id"), personId));
            linked.add(cb.equal(creator.get("id"), personId));
            if (StringUtils.hasText(idNumber)) {
                linked.add(cb.equal(root.get("idNumber"), idNumber.trim()));
            }
            if (StringUtils.hasText(mobile)) {
                linked.add(cb.equal(root.get("mobile"), mobile.trim()));
            }
            return cb.and(root.get("kind").in(kinds), cb.or(linked.toArray(Predicate[]::new)));
        };
        Page<OaRequest> result = requestRepository.findAll(
                spec,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id"))
        );
        return new PageResult<>(result.map(this::toView).getContent(), result.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public OaRequestView get(OaKind kind, Long id) {
        return toView(requireVisible(kind, id));
    }

    @Transactional
    public OaRequestView create(OaKind kind, UpsertOaRequest request, SysUser creator) {
        FormModule module = kind.module();
        if (!workflowService.hasPublished(module)) {
            throw ApiException.badRequest("请先在流程引擎发布" + module.label() + "流程");
        }
        OaRequest item = new OaRequest();
        item.setCode(nextCode(kind));
        item.setKind(kind);
        item.setCreatedBy(creator);
        item.setSubmitSource("ADMIN");
        item.setPublic(request != null && Boolean.TRUE.equals(request.isPublic()));
        apply(item, kind, request, Map.of(), true);
        OaRequest saved = requestRepository.save(item);
        workflowService.start(module, saved.getId());
        return toView(saved);
    }

    @Transactional
    public OaRequest createPublic(OaKind kind, UpsertOaRequest request, List<MultipartFile> files) {
        FormModule module = kind.module();
        if (!workflowService.hasPublished(module)) {
            throw ApiException.badRequest("请先在流程引擎发布" + module.label() + "流程");
        }
        OaRequest item = new OaRequest();
        item.setCode(nextCode(kind));
        item.setKind(kind);
        item.setCreatedBy(null);
        item.setSubmitSource("PUBLIC");
        item.setPublic(false);
        apply(item, kind, request, Map.of(), true);
        addFiles(item, files);
        OaRequest saved = requestRepository.save(item);
        workflowService.start(module, saved.getId());
        return saved;
    }

    @Transactional
    public OaRequestView update(OaKind kind, Long id, UpsertOaRequest request) {
        OaRequest item = requireVisible(kind, id);
        permissionService.requireOwnRecord(kind.module(), item.getCreatedBy());
        if (request != null && request.isPublic() != null) {
            item.setPublic(request.isPublic());
        }
        Map<String, String> modes = workflowService.writeModes(kind.module(), id);
        apply(item, kind, request, modes, false);
        return toView(item);
    }

    @Transactional
    public void delete(OaKind kind, Long id) {
        OaRequest item = requireVisible(kind, id);
        permissionService.requireOwnRecord(kind.module(), item.getCreatedBy());
        if (kind == OaKind.LOAN && requestRepository.existsByLinkRequestId(id)) {
            throw ApiException.badRequest("该借款单已有付款或还款，请先删除后再删借款单");
        }
        loanFinance.restoreIfPosted(item);
        workflowService.deleteForBiz(kind.module(), id);
        requestRepository.delete(item);
    }

    @Transactional
    public OaRequestView addFiles(OaKind kind, Long id, List<MultipartFile> files) {
        OaRequest item = requireVisible(kind, id);
        permissionService.requireOwnRecord(kind.module(), item.getCreatedBy());
        assertCanEditAttachments(kind, item);
        addFiles(item, files);
        return toView(item);
    }

    @Transactional
    public OaRequestView removeFile(OaKind kind, Long id, Long fileId) {
        OaRequest item = requireVisible(kind, id);
        permissionService.requireOwnRecord(kind.module(), item.getCreatedBy());
        assertCanEditAttachments(kind, item);
        OaRequestFile target = item.getFiles().stream()
                .filter(file -> itemId(file).equals(fileId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("附件不存在"));
        item.getFiles().remove(target);
        return toView(item);
    }

    @Transactional(readOnly = true)
    public List<OaRequest> listPublic(OaKind kind, String keyword) {
        Specification<OaRequest> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("kind"), kind),
                cb.isTrue(root.get("isPublic"))
        );
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("code")), like),
                    cb.like(cb.lower(root.get("applicantName")), like),
                    cb.like(cb.lower(root.get("reason")), like)
            ));
        }
        return requestRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "id"));
    }

    @Transactional(readOnly = true)
    public OaRequest requirePublic(OaKind kind, Long id) {
        OaRequest item = requestRepository.findById(id).orElseThrow(() -> ApiException.notFound("内容不存在"));
        if (item.getKind() != kind || !item.isPublic()) {
            throw ApiException.notFound("内容不存在");
        }
        item.getFiles().size();
        return item;
    }

    private void apply(OaRequest item, OaKind kind, UpsertOaRequest request, Map<String, String> modes, boolean creating) {
        if (!kind.guestApplicant() && (writable(modes, "staffId") || item.getStaff() == null)) {
            SysUser staff = resolveUser(request == null ? null : request.staffId(), false);
            if (staff == null && item.getCreatedBy() != null) {
                staff = item.getCreatedBy();
            }
            if (staff == null) {
                throw ApiException.badRequest("请选择申请人（系统账号）");
            }
            item.setStaff(staff);
        }
        if (kind == OaKind.ONBOARD && (writable(modes, "staffId") || item.getStaff() == null)) {
            item.setStaff(resolveUser(request == null ? null : request.staffId(), false));
        }
        if (writable(modes, "applicantName") || item.getApplicantName() == null) {
            String name = BizParse.blankToNull(request == null ? null : request.applicantName());
            if (name == null && item.getStaff() != null) {
                name = item.getStaff().getDisplayName();
            }
            item.setApplicantName(BizParse.required(name, "请填写申请人姓名"));
        }
        if (kind == OaKind.INTERVIEW || kind == OaKind.SEAL || kind == OaKind.QUALIFY) {
            if (writable(modes, "reason") || item.getReason() == null) {
                String reason = BizParse.blankToNull(request == null ? null : request.reason());
                if (reason == null) {
                    reason = kind == OaKind.SEAL ? "盖章申请" : kind == OaKind.QUALIFY ? "资质借出" : "面试申请";
                }
                item.setReason(reason);
            }
        } else if (writable(modes, "reason") || item.getReason() == null) {
            item.setReason(BizParse.required(request == null ? null : request.reason(), reasonLabel(kind)));
        }
        if (writable(modes, "expectedDate") || item.getExpectedDate() == null) {
            item.setExpectedDate(BizParse.date(request == null ? null : request.expectedDate(), dateLabel(kind)));
        }
        if (kind == OaKind.ONBOARD) {
            applyLoginAccount(item, request, modes, creating);
            if (writable(modes, "gender")) {
                item.setGender(gender(request == null ? null : request.gender()));
            }
            if (writable(modes, "mobile")) {
                item.setMobile(BizParse.required(request == null ? null : request.mobile(), "请填写手机号"));
            }
            if (writable(modes, "idNumber")) {
                item.setIdNumber(BizParse.blankToNull(request == null ? null : request.idNumber()));
            }
            if (writable(modes, "departmentId") || item.getDepartment() == null) {
                item.setDepartment(requireDept(request == null ? null : request.departmentId(), "请选择入职部门"));
            }
            if (writable(modes, "position") || item.getPosition() == null) {
                item.setPosition(BizParse.required(request == null ? null : request.position(), "请填写岗位"));
            }
            if (writable(modes, "jobLevel")) {
                item.setJobLevel(BizParse.blankToNull(request == null ? null : request.jobLevel()));
            }
            if (writable(modes, "managerId")) {
                item.setManager(resolveUser(request == null ? null : request.managerId(), false));
            }
            if (writable(modes, "probationMonths")) {
                item.setProbationMonths(request == null ? null : request.probationMonths());
            }
            item.setTargetDepartment(null);
            item.setTargetPosition(null);
            item.setTargetLevel(null);
            item.setHandover(null);
            item.setResignType(null);
            applySalary(item, kind, request, modes, creating);
            return;
        }
        if (kind == OaKind.INTERVIEW) {
            applyInterview(item, request, modes);
            return;
        }
        if (kind == OaKind.SEAL) {
            applySeal(item, request, modes);
            return;
        }
        if (kind == OaKind.QUALIFY) {
            applyQualify(item, request, modes);
            return;
        }
        if (kind == OaKind.TRIP) {
            applyTrip(item, request, modes);
            return;
        }
        if (kind == OaKind.LEAVE) {
            applyLeave(item, request, modes);
            return;
        }
        if (kind == OaKind.LOAN || kind == OaKind.LOAN_PAY || kind == OaKind.LOAN_REPAY) {
            applyLoan(item, kind, request, modes);
            return;
        }
        fillCurrentPostFromStaff(item, request, modes);
        if (kind == OaKind.TRANSFER) {
            if (writable(modes, "targetDepartmentId") || item.getTargetDepartment() == null) {
                item.setTargetDepartment(requireDept(request == null ? null : request.targetDepartmentId(), "请选择转入部门"));
            }
            if (writable(modes, "targetPosition") || item.getTargetPosition() == null) {
                item.setTargetPosition(BizParse.required(request == null ? null : request.targetPosition(), "请填写转入岗位"));
            }
            item.setTargetLevel(null);
            item.setManager(null);
            item.setHandover(null);
            item.setResignType(null);
            item.setProbationMonths(null);
            item.setGender(null);
            item.setMobile(BizParse.blankToNull(request == null ? null : request.mobile()));
            item.setIdNumber(null);
            applySalary(item, kind, request, modes, creating);
            return;
        }
        if (kind == OaKind.PROMOTION) {
            if (writable(modes, "targetPosition") || item.getTargetPosition() == null) {
                item.setTargetPosition(BizParse.required(request == null ? null : request.targetPosition(), "请填写拟晋升岗位"));
            }
            if (writable(modes, "targetLevel") || item.getTargetLevel() == null) {
                item.setTargetLevel(BizParse.required(request == null ? null : request.targetLevel(), "请填写拟晋升职级"));
            }
            if (writable(modes, "targetDepartmentId")) {
                Long targetDeptId = request == null ? null : request.targetDepartmentId();
                item.setTargetDepartment(targetDeptId == null ? item.getDepartment() : orgService.require(targetDeptId));
            } else if (item.getTargetDepartment() == null) {
                item.setTargetDepartment(item.getDepartment());
            }
            item.setManager(null);
            item.setHandover(null);
            item.setResignType(null);
            item.setProbationMonths(null);
            item.setGender(null);
            item.setMobile(BizParse.blankToNull(request == null ? null : request.mobile()));
            item.setIdNumber(null);
            applySalary(item, kind, request, modes, creating);
            return;
        }
        if (kind == OaKind.SALARY) {
            item.setTargetDepartment(null);
            item.setTargetPosition(null);
            item.setTargetLevel(null);
            item.setManager(null);
            item.setHandover(null);
            item.setResignType(null);
            item.setProbationMonths(null);
            item.setGender(null);
            item.setMobile(BizParse.blankToNull(request == null ? null : request.mobile()));
            item.setIdNumber(null);
            applySalary(item, kind, request, modes, creating);
            return;
        }
        if (writable(modes, "resignType") || item.getResignType() == null) {
            item.setResignType(resignType(request == null ? null : request.resignType()));
        }
        if (writable(modes, "handoverUserId")) {
            item.setHandover(resolveUser(request == null ? null : request.handoverUserId(), false));
        }
        item.setTargetDepartment(null);
        item.setTargetPosition(null);
        item.setTargetLevel(null);
        item.setManager(null);
        item.setProbationMonths(null);
        item.setGender(null);
        item.setMobile(BizParse.blankToNull(request == null ? null : request.mobile()));
        item.setIdNumber(null);
    }

    private void fillCurrentPostFromStaff(OaRequest item, UpsertOaRequest request, Map<String, String> modes) {
        if (writable(modes, "departmentId") || item.getDepartment() == null) {
            Long deptId = request == null ? null : request.departmentId();
            if (deptId == null && item.getStaff() != null && item.getStaff().getDepartment() != null) {
                deptId = item.getStaff().getDepartment().getId();
            }
            item.setDepartment(requireDept(deptId, "请选择现部门"));
        }
        if (writable(modes, "position") || item.getPosition() == null) {
            String position = BizParse.blankToNull(request == null ? null : request.position());
            if (position == null && item.getStaff() != null) {
                position = item.getStaff().getPosition();
            }
            item.setPosition(BizParse.required(position, "请填写现岗位"));
        }
        if (writable(modes, "jobLevel")) {
            String level = BizParse.blankToNull(request == null ? null : request.jobLevel());
            if (level == null && item.getStaff() != null) {
                level = item.getStaff().getJobLevel();
            }
            item.setJobLevel(level);
        }
    }

    private void applySalary(OaRequest item, OaKind kind, UpsertOaRequest request, Map<String, String> modes, boolean creating) {
        if (!kind.hasSalary()) {
            return;
        }
        if (creating && kind == OaKind.ONBOARD) {
            return;
        }
        if (creating) {
            copySalaryFromStaff(item);
            return;
        }
        if (writable(modes, "salary")) {
            item.setSalary(request == null ? item.getSalary() : request.salary());
        }
        if (writable(modes, "salaryLines")) {
            replaceSalaryLines(item, request == null ? null : request.salaryLines());
            if (item.getSalary() == null) {
                item.setSalary(sumSalary(item.getSalaryLines()));
            }
        }
    }

    private void applyInterview(OaRequest item, UpsertOaRequest request, Map<String, String> modes) {
        item.setStaff(null);
        item.setDepartment(null);
        item.setPosition(null);
        item.setJobLevel(null);
        item.setTargetDepartment(null);
        item.setTargetPosition(null);
        item.setTargetLevel(null);
        item.setManager(null);
        item.setHandover(null);
        item.setResignType(null);
        item.setProbationMonths(null);
        item.setLoginUsername(null);
        item.setSalary(null);
        UpsertInterviewRequest interview = request == null ? null : request.interview();
        if (writable(modes, "gender")) {
            item.setGender(gender(request == null ? null : request.gender()));
        }
        if (writable(modes, "mobile")) {
            item.setMobile(BizParse.required(request == null ? null : request.mobile(), "请填写联系电话"));
        }
        if (writable(modes, "idNumber")) {
            item.setIdNumber(BizParse.blankToNull(request == null ? null : request.idNumber()));
        }
        if (writable(modes, "birthDate")) {
            item.setBirthDate(interview == null || !StringUtils.hasText(interview.birthDate())
                    ? null
                    : BizParse.date(interview.birthDate(), "请选择出生日期"));
        }
        if (writable(modes, "ethnicity")) {
            item.setEthnicity(BizParse.blankToNull(interview == null ? null : interview.ethnicity()));
        }
        if (writable(modes, "maritalStatus")) {
            item.setMaritalStatus(maritalStatus(interview == null ? null : interview.maritalStatus()));
        }
        if (writable(modes, "email")) {
            item.setEmail(BizParse.blankToNull(interview == null ? null : interview.email()));
        }
        if (writable(modes, "hukouAddress")) {
            item.setHukouAddress(BizParse.blankToNull(interview == null ? null : interview.hukouAddress()));
        }
        if (writable(modes, "currentAddress")) {
            item.setCurrentAddress(BizParse.blankToNull(interview == null ? null : interview.currentAddress()));
        }
        if (writable(modes, "school")) {
            item.setSchool(BizParse.blankToNull(interview == null ? null : interview.school()));
        }
        if (writable(modes, "major")) {
            item.setMajor(BizParse.blankToNull(interview == null ? null : interview.major()));
        }
        if (writable(modes, "highestEducation")) {
            item.setHighestEducation(BizParse.blankToNull(interview == null ? null : interview.highestEducation()));
        }
        if (writable(modes, "educationNote")) {
            item.setEducationNote(BizParse.blankToNull(interview == null ? null : interview.educationNote()));
        }
        if (writable(modes, "educationLines") || writable(modes, "workLines")
                || writable(modes, "familyLines") || writable(modes, "trainingLines")) {
            replaceResumeLines(item, interview);
        }
    }

    private void replaceResumeLines(OaRequest item, UpsertInterviewRequest interview) {
        if (item.getResumeLines() == null) {
            item.setResumeLines(new ArrayList<>());
        }
        item.getResumeLines().clear();
        if (interview == null) {
            return;
        }
        addResumeLines(item, OaResumeLine.EDUCATION, interview.educationLines());
        addResumeLines(item, OaResumeLine.WORK, interview.workLines());
        addResumeLines(item, OaResumeLine.FAMILY, interview.familyLines());
        addResumeLines(item, OaResumeLine.TRAINING, interview.trainingLines());
    }

    private void applyOfficeStaff(OaRequest item, UpsertOaRequest request, Map<String, String> modes) {
        item.setTargetDepartment(null);
        item.setTargetPosition(null);
        item.setTargetLevel(null);
        item.setManager(null);
        item.setHandover(null);
        item.setResignType(null);
        item.setProbationMonths(null);
        item.setGender(null);
        item.setIdNumber(null);
        item.setLoginUsername(null);
        item.setSalary(null);
        if (writable(modes, "departmentId") || item.getDepartment() == null) {
            Long deptId = request == null ? null : request.departmentId();
            if (deptId == null && item.getStaff() != null && item.getStaff().getDepartment() != null) {
                deptId = item.getStaff().getDepartment().getId();
            }
            item.setDepartment(deptId == null ? null : orgService.require(deptId));
        }
        if (writable(modes, "position") || item.getPosition() == null) {
            String position = BizParse.blankToNull(request == null ? null : request.position());
            if (position == null && item.getStaff() != null) {
                position = item.getStaff().getPosition();
            }
            item.setPosition(position);
        }
        if (writable(modes, "jobLevel")) {
            String level = BizParse.blankToNull(request == null ? null : request.jobLevel());
            if (level == null && item.getStaff() != null) {
                level = item.getStaff().getJobLevel();
            }
            item.setJobLevel(level);
        }
        if (writable(modes, "mobile")) {
            item.setMobile(BizParse.blankToNull(request == null ? null : request.mobile()));
        }
    }

    private void applySeal(OaRequest item, UpsertOaRequest request, Map<String, String> modes) {
        applyOfficeStaff(item, request, modes);
        UpsertSealRequest seal = request == null ? null : request.seal();
        OaFormExtra current = readExtra(item);
        String belongCompany = writable(modes, "belongCompany") || blank(current.belongCompany())
                ? requiredIn(OaFormOptions.COMPANIES, seal == null ? null : seal.belongCompany(), "请选择归属公司")
                : current.belongCompany();
        List<String> sealTypes = writable(modes, "sealTypes") || empty(current.sealTypes())
                ? requiredMany(OaFormOptions.SEAL_TYPES, seal == null ? null : seal.sealTypes(), "请选择盖章类型")
                : current.sealTypes();
        String copyCount = writable(modes, "copyCount") || blank(current.copyCount())
                ? BizParse.required(seal == null ? null : seal.copyCount(), "请填写盖章文件份数")
                : current.copyCount();
        writeExtra(item, current.withSeal(belongCompany, sealTypes, copyCount));
    }

    private void applyQualify(OaRequest item, UpsertOaRequest request, Map<String, String> modes) {
        applyOfficeStaff(item, request, modes);
        UpsertQualifyRequest qualify = request == null ? null : request.qualify();
        OaFormExtra current = readExtra(item);
        String lendDate = writable(modes, "lendDate") || blank(current.lendDate())
                ? BizParse.date(qualify == null ? null : qualify.lendDate(), "请选择外借时间").toString()
                : current.lendDate();
        String returnDate = writable(modes, "returnDate")
                ? (qualify == null || !StringUtils.hasText(qualify.returnDate())
                        ? null
                        : BizParse.date(qualify.returnDate(), "请选择归还时间").toString())
                : current.returnDate();
        List<String> companies = writable(modes, "qualifyCompanies") || empty(current.qualifyCompanies())
                ? requiredMany(OaFormOptions.COMPANIES, qualify == null ? null : qualify.companies(), "请选择出借资质公司")
                : current.qualifyCompanies();
        List<String> items = writable(modes, "qualifyItems") || empty(current.qualifyItems())
                ? requiredMany(OaFormOptions.QUALIFY_ITEMS, qualify == null ? null : qualify.items(), "请选择出借资质")
                : current.qualifyItems();
        Long companyAuditorId = writable(modes, "companyAuditorId")
                ? optionalUserId(qualify == null ? null : qualify.companyAuditorId())
                : current.companyAuditorId();
        Long financeAuditorId = writable(modes, "financeAuditorId")
                ? optionalUserId(qualify == null ? null : qualify.financeAuditorId())
                : current.financeAuditorId();
        Long contractAuditorId = writable(modes, "contractAuditorId")
                ? optionalUserId(qualify == null ? null : qualify.contractAuditorId())
                : current.contractAuditorId();
        writeExtra(item, current.withQualify(
                lendDate,
                returnDate,
                companies,
                items,
                companyAuditorId,
                financeAuditorId,
                contractAuditorId,
                writable(modes, "companyAuditorNote")
                        ? BizParse.blankToNull(qualify == null ? null : qualify.companyAuditorNote())
                        : current.companyAuditorNote(),
                writable(modes, "financeAuditorNote")
                        ? BizParse.blankToNull(qualify == null ? null : qualify.financeAuditorNote())
                        : current.financeAuditorNote(),
                writable(modes, "contractAuditorNote")
                        ? BizParse.blankToNull(qualify == null ? null : qualify.contractAuditorNote())
                        : current.contractAuditorNote(),
                writable(modes, "contractDesc")
                        ? BizParse.blankToNull(qualify == null ? null : qualify.contractDesc())
                        : current.contractDesc(),
                writable(modes, "otherCertNote")
                        ? BizParse.blankToNull(qualify == null ? null : qualify.otherCertNote())
                        : current.otherCertNote()
        ));
    }

    private void applyTrip(OaRequest item, UpsertOaRequest request, Map<String, String> modes) {
        applyOfficeStaff(item, request, modes);
        if (writable(modes, "departmentId") || item.getDepartment() == null) {
            item.setDepartment(requireDept(request == null ? null : request.departmentId(), "请选择部门"));
        }
        if (writable(modes, "managerId")) {
            item.setManager(resolveUser(request == null ? null : request.managerId(), false));
        }
        UpsertTripRequest trip = request == null ? null : request.trip();
        OaFormExtra current = readExtra(item);
        List<Long> companions = writable(modes, "companionIds")
                ? validUserIds(trip == null ? null : trip.companionIds())
                : listOrEmpty(current.companionIds());
        String fromCity = writable(modes, "fromCity") || blank(current.fromCity())
                ? BizParse.required(trip == null ? null : trip.fromCity(), "请填写出发城市")
                : current.fromCity();
        String toCity = writable(modes, "toCity") || blank(current.toCity())
                ? BizParse.required(trip == null ? null : trip.toCity(), "请填写目的地城市")
                : current.toCity();
        String travelMode = writable(modes, "travelMode") || blank(current.travelMode())
                ? requiredIn(
                        OaFormOptions.TRAVEL_MODES,
                        blank(trip == null ? null : trip.travelMode()) ? "PUBLIC" : trip.travelMode(),
                        "请选择出行方式")
                : current.travelMode();
        String plateNo = writable(modes, "plateNo")
                ? (trip == null || !StringUtils.hasText(trip.plateNo()) ? "无" : trip.plateNo().trim())
                : (blank(current.plateNo()) ? "无" : current.plateNo());
        java.math.BigDecimal mileage = writable(modes, "mileage")
                ? (trip == null || trip.mileage() == null ? java.math.BigDecimal.ZERO : trip.mileage())
                : (current.mileage() == null ? java.math.BigDecimal.ZERO : current.mileage());
        String returnPlanDate = writable(modes, "returnPlanDate")
                ? optionalDate(trip == null ? null : trip.returnPlanDate(), "请选择预计返回日期")
                : current.returnPlanDate();
        String reviewTime = writable(modes, "reviewTime")
                ? optionalDate(trip == null ? null : trip.reviewTime(), "请选择审核时间")
                : current.reviewTime();
        String actualReturnDate = writable(modes, "actualReturnDate")
                ? optionalDate(trip == null ? null : trip.actualReturnDate(), "请选择实际返回日期")
                : current.actualReturnDate();
        java.math.BigDecimal durationHours = writable(modes, "durationHours") || current.durationHours() == null
                ? requiredHours(trip == null ? null : trip.durationHours())
                : current.durationHours();
        String remark = writable(modes, "remark")
                ? BizParse.blankToNull(trip == null ? null : trip.remark())
                : current.remark();
        writeExtra(item, current.withTrip(
                companions,
                fromCity,
                toCity,
                travelMode,
                plateNo,
                mileage,
                returnPlanDate,
                reviewTime,
                actualReturnDate,
                durationHours,
                remark
        ));
    }

    private void applyLeave(OaRequest item, UpsertOaRequest request, Map<String, String> modes) {
        applyOfficeStaff(item, request, modes);
        if (writable(modes, "departmentId") || item.getDepartment() == null) {
            item.setDepartment(requireDept(request == null ? null : request.departmentId(), "请选择部门"));
        }
        UpsertLeaveRequest body = request == null ? null : request.leave();
        OaFormExtra current = readExtra(item);
        OaLeaveInfo previous = current.leave();
        String leaveType = writable(modes, "leaveType") || previous == null || blank(previous.leaveType())
                ? requiredIn(OaFormOptions.LEAVE_TYPES, body == null ? null : body.leaveType(), "请选择请假事由")
                : previous.leaveType();
        boolean bereavement = "BEREAVEMENT".equals(leaveType);
        String kinship = writable(modes, "kinship") || previous == null
                ? (bereavement
                        ? requiredIn(OaFormOptions.KINSHIPS, body == null ? null : body.kinship(), "请选择亲属关系")
                        : BizParse.blankToNull(body == null ? null : body.kinship()))
                : previous.kinship();
        if (kinship != null && !OaFormOptions.KINSHIPS.contains(kinship)) {
            throw ApiException.badRequest("请选择亲属关系");
        }
        java.math.BigDecimal holidayDays = writable(modes, "holidayDays")
                ? optionalDays(body == null ? null : body.holidayDays(), "节假日天数")
                : (previous == null ? java.math.BigDecimal.ZERO : previous.holidayDays());
        boolean includeCompensatory = writable(modes, "includeCompensatory") || previous == null
                ? body != null && Boolean.TRUE.equals(body.includeCompensatory())
                : Boolean.TRUE.equals(previous.includeCompensatory());
        java.math.BigDecimal compensatoryDays = writable(modes, "compensatoryDays")
                ? optionalDays(body == null ? null : body.compensatoryDays(), "调休天数")
                : (previous == null ? null : previous.compensatoryDays());
        if (includeCompensatory && (compensatoryDays == null || compensatoryDays.compareTo(java.math.BigDecimal.ZERO) <= 0)) {
            throw ApiException.badRequest("请填写调休天数");
        }
        String month = writable(modes, "month")
                ? BizParse.blankToNull(body == null ? null : body.month())
                : (previous == null ? null : previous.month());
        String endDate = writable(modes, "endDate") || previous == null || blank(previous.endDate())
                ? requiredDate(body == null ? null : body.endDate(), "请选择预计结束日期")
                : previous.endDate();
        java.math.BigDecimal leaveDays = writable(modes, "leaveDays") || previous == null || previous.leaveDays() == null
                ? requiredDays(body == null ? null : body.leaveDays(), "请填写预计请假天数")
                : previous.leaveDays();
        String actualEndDate = writable(modes, "actualEndDate")
                ? optionalDate(body == null ? null : body.actualEndDate(), "请选择结束日期")
                : (previous == null ? null : previous.actualEndDate());
        java.math.BigDecimal actualDays = writable(modes, "actualDays")
                ? optionalDays(body == null ? null : body.actualDays(), "实际请假天数")
                : (previous == null ? null : previous.actualDays());
        writeExtra(item, current.withLeave(new OaLeaveInfo(
                holidayDays == null ? java.math.BigDecimal.ZERO : holidayDays,
                includeCompensatory,
                compensatoryDays,
                month,
                leaveType,
                kinship,
                endDate,
                leaveDays,
                actualEndDate,
                actualDays
        )));
    }

    private void applyLoan(OaRequest item, OaKind kind, UpsertOaRequest request, Map<String, String> modes) {
        applyOfficeStaff(item, request, modes);
        if (writable(modes, "departmentId") || item.getDepartment() == null) {
            item.setDepartment(requireDept(request == null ? null : request.departmentId(), "请选择部门"));
        }
        UpsertLoanRequest body = request == null ? null : request.loan();
        OaFormExtra current = readExtra(item);
        OaLoanMoney previous = current.loan();
        if (kind == OaKind.LOAN) {
            boolean linkProject = writable(modes, "linkProject") || previous == null
                    ? body != null && Boolean.TRUE.equals(body.linkProject())
                    : Boolean.TRUE.equals(previous.linkProject());
            Long projectId = writable(modes, "projectId") || previous == null
                    ? (body == null ? null : body.projectId())
                    : previous.projectId();
            Project project = loanFinance.optionalProject(linkProject, projectId, previous == null ? null : previous.projectId());
            if (linkProject && project == null) {
                throw ApiException.badRequest("请选择关联项目");
            }
            Customer customer = loanFinance.optionalCustomer(body == null ? null : body.customerId(), project);
            if (linkProject && customer == null) {
                throw ApiException.badRequest("请选择客户名称");
            }
            String belongCompany = writable(modes, "belongCompany") || blank(current.belongCompany())
                    ? requiredIn(OaFormOptions.COMPANIES, body == null ? null : body.belongCompany(), "请选择借款公司")
                    : current.belongCompany();
            BigDecimal amount = writable(modes, "amount") || previous == null || previous.amount() == null
                    ? OaLoanFinanceService.money(body == null ? null : body.amount())
                    : previous.amount();
            BigDecimal payAmount = writable(modes, "payAmount") || previous == null || previous.payAmount() == null
                    ? OaLoanFinanceService.money(body == null ? null : body.payAmount())
                    : previous.payAmount();
            Long accountId = writable(modes, "accountId") || previous == null || previous.accountId() == null
                    ? loanFinance.requireAccount(body == null ? null : body.accountId()).getId()
                    : previous.accountId();
            OaLoanMoney next = new OaLoanMoney(
                    linkProject,
                    project == null ? null : project.getId(),
                    customer == null ? null : customer.getId(),
                    amount,
                    payAmount,
                    accountId,
                    null,
                    null
            );
            loanFinance.assertCanCreate(item, kind, next, item.getId());
            item.setLinkRequestId(null);
            writeExtra(item, current.withLoan(belongCompany, next));
            return;
        }
        Long loanRequestId = writable(modes, "loanRequestId") || item.getLinkRequestId() == null
                ? (body == null ? null : body.loanRequestId())
                : item.getLinkRequestId();
        OaRequest loan = loanFinance.requireLoan(loanRequestId);
        item.setLinkRequestId(loan.getId());
        Long accountId = writable(modes, "accountId") || previous == null || previous.accountId() == null
                ? loanFinance.requireAccount(body == null ? null : body.accountId()).getId()
                : previous.accountId();
        OaLoanMoney next;
        if (kind == OaKind.LOAN_PAY) {
            BigDecimal payAmount = writable(modes, "payAmount") || previous == null || previous.payAmount() == null
                    ? OaLoanFinanceService.money(body == null ? null : body.payAmount())
                    : previous.payAmount();
            next = new OaLoanMoney(null, null, null, null, payAmount, accountId, loan.getId(), null);
        } else {
            BigDecimal repayAmount = writable(modes, "repayAmount") || previous == null || previous.repayAmount() == null
                    ? OaLoanFinanceService.money(body == null ? null : body.repayAmount())
                    : previous.repayAmount();
            next = new OaLoanMoney(null, null, null, null, null, accountId, loan.getId(), repayAmount);
        }
        loanFinance.assertCanCreate(item, kind, next, item.getId());
        writeExtra(item, current.withLoan(current.belongCompany(), next));
    }

    private OaLoanView loanView(OaRequest item) {
        if (item.getKind() != OaKind.LOAN && item.getKind() != OaKind.LOAN_PAY && item.getKind() != OaKind.LOAN_REPAY) {
            return null;
        }
        return loanFinance.toView(item);
    }

    private OaSealView sealView(OaRequest item) {
        if (item.getKind() != OaKind.SEAL) {
            return null;
        }
        OaFormExtra extra = readExtra(item);
        return new OaSealView(extra.belongCompany(), extra.sealTypes(), extra.copyCount());
    }

    private OaQualifyView qualifyView(OaRequest item) {
        if (item.getKind() != OaKind.QUALIFY) {
            return null;
        }
        OaFormExtra extra = readExtra(item);
        return new OaQualifyView(
                extra.lendDate(),
                extra.returnDate(),
                extra.qualifyCompanies(),
                extra.qualifyItems(),
                extra.companyAuditorId(),
                userName(extra.companyAuditorId()),
                extra.financeAuditorId(),
                userName(extra.financeAuditorId()),
                extra.contractAuditorId(),
                userName(extra.contractAuditorId()),
                extra.companyAuditorNote(),
                extra.financeAuditorNote(),
                extra.contractAuditorNote(),
                extra.contractDesc(),
                extra.otherCertNote()
        );
    }

    private OaTripView tripView(OaRequest item) {
        if (item.getKind() != OaKind.TRIP) {
            return null;
        }
        OaFormExtra extra = readExtra(item);
        List<Long> companions = listOrEmpty(extra.companionIds());
        return new OaTripView(
                companions,
                companions.stream().map(this::userName).toList(),
                extra.fromCity(),
                extra.toCity(),
                extra.travelMode(),
                OaFormOptions.travelModeLabel(extra.travelMode()),
                extra.plateNo(),
                extra.mileage(),
                extra.returnPlanDate(),
                extra.reviewTime(),
                extra.actualReturnDate(),
                extra.durationHours(),
                extra.remark()
        );
    }

    private OaLeaveView leaveView(OaRequest item) {
        if (item.getKind() != OaKind.LEAVE) {
            return null;
        }
        OaLeaveInfo extra = readExtra(item).leave();
        if (extra == null) {
            return null;
        }
        return new OaLeaveView(
                extra.holidayDays(),
                extra.includeCompensatory(),
                extra.compensatoryDays(),
                extra.month(),
                extra.leaveType(),
                OaFormOptions.leaveTypeLabel(extra.leaveType()),
                extra.kinship(),
                OaFormOptions.kinshipLabel(extra.kinship()),
                extra.endDate(),
                extra.leaveDays(),
                extra.actualEndDate(),
                extra.actualDays()
        );
    }

    private Long optionalUserId(Long id) {
        return resolveUser(id, false) == null ? null : id;
    }

    private String userName(Long id) {
        if (id == null) {
            return null;
        }
        return userRepository.findById(id).map(SysUser::getDisplayName).orElse(null);
    }

    private OaFormExtra readExtra(OaRequest item) {
        String raw = item.getExtraJson();
        if (!StringUtils.hasText(raw)) {
            return emptyExtra();
        }
        try {
            OaFormExtra extra = tools.jackson.databind.json.JsonMapper.shared().readValue(raw, OaFormExtra.class);
            return extra == null ? emptyExtra() : extra;
        } catch (Exception ex) {
            return emptyExtra();
        }
    }

    private void writeExtra(OaRequest item, OaFormExtra extra) {
        try {
            item.setExtraJson(tools.jackson.databind.json.JsonMapper.shared().writeValueAsString(extra));
        } catch (Exception ex) {
            throw ApiException.badRequest("申请内容保存失败");
        }
    }

    private static OaFormExtra emptyExtra() {
        return OaFormExtra.empty();
    }

    private static boolean blank(String value) {
        return !StringUtils.hasText(value);
    }

    private static boolean empty(List<String> values) {
        return values == null || values.isEmpty();
    }

    private static List<Long> listOrEmpty(List<Long> values) {
        return values == null ? List.of() : values;
    }

    private List<Long> validUserIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().filter(id -> resolveUser(id, false) != null).distinct().toList();
    }

    private static String optionalDate(String raw, String label) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return BizParse.date(raw, label).toString();
    }

    private static String requiredDate(String raw, String label) {
        return BizParse.date(raw, label).toString();
    }

    private static java.math.BigDecimal requiredDays(java.math.BigDecimal days, String message) {
        java.math.BigDecimal value = optionalDays(days, message);
        if (value == null || value.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest(message);
        }
        return value;
    }

    private static java.math.BigDecimal optionalDays(java.math.BigDecimal days, String label) {
        if (days == null) {
            return null;
        }
        if (days.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest(label + "不能为负数");
        }
        return days;
    }

    private static java.math.BigDecimal requiredHours(java.math.BigDecimal hours) {
        if (hours == null) {
            throw ApiException.badRequest("请填写出差时长");
        }
        return hours;
    }

    private static String requiredIn(List<String> allowed, String raw, String message) {
        String value = BizParse.required(raw, message);
        if (!allowed.contains(value)) {
            throw ApiException.badRequest(message);
        }
        return value;
    }

    private static List<String> requiredMany(List<String> allowed, List<String> raw, String message) {
        if (raw == null || raw.isEmpty()) {
            throw ApiException.badRequest(message);
        }
        List<String> picked = raw.stream().filter(allowed::contains).distinct().toList();
        if (picked.isEmpty()) {
            throw ApiException.badRequest(message);
        }
        return picked;
    }

    private void addResumeLines(OaRequest item, String kind, List<UpsertOaResumeLine> lines) {
        if (lines == null) {
            return;
        }
        int order = 0;
        for (UpsertOaResumeLine line : lines) {
            if (line == null) {
                continue;
            }
            String title = BizParse.blankToNull(line.title());
            if (title == null) {
                continue;
            }
            OaResumeLine row = new OaResumeLine();
            row.setRequest(item);
            row.setLineKind(kind);
            row.setTitle(title);
            row.setSubtitle(BizParse.blankToNull(line.subtitle()));
            row.setExtra(BizParse.blankToNull(line.extra()));
            row.setStartYm(BizParse.blankToNull(line.startYm()));
            row.setEndYm(BizParse.blankToNull(line.endYm()));
            row.setSortOrder(order++);
            item.getResumeLines().add(row);
        }
    }

    private OaInterviewView interviewView(OaRequest item) {
        if (item.getKind() != OaKind.INTERVIEW) {
            return null;
        }
        List<OaResumeLine> lines = item.getResumeLines() == null ? List.of() : item.getResumeLines();
        return new OaInterviewView(
                item.getBirthDate(),
                item.getEthnicity(),
                item.getMaritalStatus(),
                maritalStatusLabel(item.getMaritalStatus()),
                item.getEmail(),
                item.getHukouAddress(),
                item.getCurrentAddress(),
                item.getSchool(),
                item.getMajor(),
                item.getHighestEducation(),
                item.getEducationNote(),
                resumeViews(lines, OaResumeLine.EDUCATION),
                resumeViews(lines, OaResumeLine.WORK),
                resumeViews(lines, OaResumeLine.FAMILY),
                resumeViews(lines, OaResumeLine.TRAINING)
        );
    }

    private static List<OaResumeLineView> resumeViews(List<OaResumeLine> lines, String kind) {
        return lines.stream()
                .filter(line -> kind.equals(line.getLineKind()))
                .map(OaResumeLineView::from)
                .toList();
    }

    private static String maritalStatus(String raw) {
        String value = BizParse.blankToNull(raw);
        if (value == null) {
            return null;
        }
        String upper = value.trim().toUpperCase();
        if ("SINGLE".equals(upper) || "未婚".equals(value)) {
            return "SINGLE";
        }
        if ("MARRIED".equals(upper) || "已婚".equals(value)) {
            return "MARRIED";
        }
        if ("OTHER".equals(upper) || "其他".equals(value)) {
            return "OTHER";
        }
        throw ApiException.badRequest("婚否只能是未婚、已婚或其他");
    }

    private static String maritalStatusLabel(String status) {
        if ("SINGLE".equals(status)) {
            return "未婚";
        }
        if ("MARRIED".equals(status)) {
            return "已婚";
        }
        if ("OTHER".equals(status)) {
            return "其他";
        }
        return status;
    }

    private void copySalaryFromStaff(OaRequest item) {
        SysUser staff = item.getStaff();
        if (staff == null) {
            return;
        }
        item.setSalary(staff.getSalary());
        List<UpsertOaSalaryLine> lines = staff.getSalaryItems() == null
                ? List.of()
                : staff.getSalaryItems().stream()
                        .map(line -> new UpsertOaSalaryLine(line.getItemName(), line.getAmount()))
                        .toList();
        replaceSalaryLines(item, lines);
    }

    private void replaceSalaryLines(OaRequest item, List<UpsertOaSalaryLine> lines) {
        if (item.getSalaryLines() == null) {
            item.setSalaryLines(new ArrayList<>());
        }
        item.getSalaryLines().clear();
        if (lines == null) {
            return;
        }
        int order = 0;
        for (UpsertOaSalaryLine line : lines) {
            String name = BizParse.blankToNull(line == null ? null : line.itemName());
            if (name == null || line.amount() == null) {
                continue;
            }
            OaSalaryLine row = new OaSalaryLine();
            row.setRequest(item);
            row.setItemName(name);
            row.setAmount(line.amount());
            row.setSortOrder(order++);
            item.getSalaryLines().add(row);
        }
    }

    private static BigDecimal sumSalary(List<OaSalaryLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        return lines.stream()
                .map(OaSalaryLine::getAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void applyLoginAccount(OaRequest item, UpsertOaRequest request, Map<String, String> modes, boolean creating) {
        if (writable(modes, "loginUsername") || item.getLoginUsername() == null) {
            String username = BizParse.blankToNull(request == null ? null : request.loginUsername());
            AuthService.validateUsername(username);
            assertUsernameAvailable(username, item.getId());
            item.setLoginUsername(username);
        }
        String password = request == null ? null : request.password();
        if (StringUtils.hasText(password)) {
            AuthService.validatePassword(password);
            item.setPasswordHash(passwordEncoder.encode(password));
        } else if (creating || !StringUtils.hasText(item.getPasswordHash())) {
            throw ApiException.badRequest("请设置登录密码");
        }
    }

    private void assertUsernameAvailable(String username, Long selfId) {
        if (userRepository.existsByUsername(username)) {
            throw ApiException.badRequest("登录账号已被占用");
        }
        boolean pending = selfId == null
                ? requestRepository.existsByLoginUsernameAndAppliedFalse(username)
                : requestRepository.existsByLoginUsernameAndAppliedFalseAndIdNot(username, selfId);
        if (pending) {
            throw ApiException.badRequest("该登录账号已有待审核的入职申请");
        }
    }

    private OrgDepartment requireDept(Long id, String message) {
        if (id == null) {
            throw ApiException.badRequest(message);
        }
        return orgService.require(id);
    }

    private SysUser resolveUser(Long id, boolean required) {
        if (id == null) {
            if (required) {
                throw ApiException.badRequest("请选择人员");
            }
            return null;
        }
        return userRepository.findById(id)
                .filter(user -> user.getStatus() == UserStatus.ENABLED)
                .orElseThrow(() -> ApiException.badRequest("请选择有效人员"));
    }

    private void assertCanEditAttachments(OaKind kind, OaRequest item) {
        Map<String, String> modes = workflowService.writeModes(kind.module(), item.getId());
        if (!writable(modes, "attachments")) {
            throw ApiException.badRequest("当前流程节点不能修改附件");
        }
    }

    private void addFiles(OaRequest item, List<MultipartFile> files) {
        if (files == null) {
            return;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            FileStorageService.StoredFile stored = fileStorageService.saveOaFile(file);
            OaRequestFile attachment = new OaRequestFile();
            attachment.setRequest(item);
            attachment.setOriginalName(stored.originalName());
            attachment.setUrl(stored.url());
            attachment.setSize(stored.size());
            attachment.setContentType(stored.contentType());
            item.getFiles().add(attachment);
        }
    }

    private OaRequest requireVisible(OaKind kind, Long id) {
        OaRequest item = requestRepository.findById(id).orElseThrow(() -> ApiException.notFound("申请不存在"));
        if (item.getKind() != kind) {
            throw ApiException.notFound("申请不存在");
        }
        permissionService.requireOwnRecord(kind.module(), item.getCreatedBy());
        item.getFiles().size();
        item.getSalaryLines().size();
        item.getResumeLines().size();
        return item;
    }

    public OaRequestView toView(OaRequest item) {
        WorkflowInstance instance = instanceRepository.findByFormModuleAndBizId(item.getKind().module(), item.getId()).orElse(null);
        String status = instance == null ? null : instance.getStatus();
        return new OaRequestView(
                item.getId(),
                item.getCode(),
                item.getKind().name(),
                item.getKind().label(),
                item.getApplicantName(),
                item.getStaff() == null ? null : item.getStaff().getId(),
                item.getStaff() == null ? null : item.getStaff().getDisplayName(),
                item.getGender(),
                genderLabel(item.getGender()),
                item.getMobile(),
                item.getIdNumber(),
                item.getDepartment() == null ? null : item.getDepartment().getId(),
                item.getDepartment() == null ? null : item.getDepartment().getName(),
                item.getPosition(),
                item.getJobLevel(),
                item.getTargetDepartment() == null ? null : item.getTargetDepartment().getId(),
                item.getTargetDepartment() == null ? null : item.getTargetDepartment().getName(),
                item.getTargetPosition(),
                item.getTargetLevel(),
                item.getExpectedDate(),
                item.getManager() == null ? null : item.getManager().getId(),
                item.getManager() == null ? null : item.getManager().getDisplayName(),
                item.getHandover() == null ? null : item.getHandover().getId(),
                item.getHandover() == null ? null : item.getHandover().getDisplayName(),
                item.getProbationMonths(),
                item.getResignType(),
                resignTypeLabel(item.getResignType()),
                item.getReason(),
                item.getSalary(),
                item.getSalaryLines() == null
                        ? List.of()
                        : item.getSalaryLines().stream().map(OaSalaryLineView::from).toList(),
                interviewView(item),
                sealView(item),
                qualifyView(item),
                tripView(item),
                loanView(item),
                leaveView(item),
                item.isPublic(),
                item.getSubmitSource(),
                item.getLoginUsername(),
                StringUtils.hasText(item.getPasswordHash()),
                item.isApplied(),
                status,
                workflowService.statusLabel(status),
                item.getFiles() == null
                        ? List.of()
                        : item.getFiles().stream()
                                .map(file -> new AttachmentView(
                                        file.getId(),
                                        file.getOriginalName(),
                                        file.getUrl(),
                                        file.getSize(),
                                        file.getContentType()
                                ))
                                .toList(),
                item.getCreatedAt()
        );
    }

    private String nextCode(OaKind kind) {
        String prefix = kind.codePrefix() + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 1; i < 1000; i++) {
            String code = prefix + String.format("%03d", i);
            if (!requestRepository.existsByCode(code)) {
                return code;
            }
        }
        throw ApiException.badRequest("当日申请过多，请稍后重试");
    }

    private static Long itemId(OaRequestFile file) {
        return file.getId() == null ? -1L : file.getId();
    }

    private static String reasonLabel(OaKind kind) {
        return switch (kind) {
            case ONBOARD -> "请填写入职说明";
            case TRANSFER -> "请填写转岗原因";
            case PROMOTION -> "请填写晋升理由";
            case SALARY -> "请填写调薪原因";
            case INTERVIEW -> "请填写面试说明";
            case SEAL -> "请填写用途说明";
            case QUALIFY -> "请填写外借用途说明";
            case TRIP -> "请填写出差事由说明";
            case LEAVE -> "请填写事由说明";
            case LOAN -> "请填写用途";
            case LOAN_PAY -> "请填写付款说明";
            case LOAN_REPAY -> "请填写还款说明";
            case RESIGN -> "请填写离职原因";
        };
    }

    private static String dateLabel(OaKind kind) {
        return switch (kind) {
            case ONBOARD -> "请选择入职日期";
            case TRANSFER -> "请选择期望转岗日";
            case PROMOTION -> "请选择期望生效日";
            case SALARY -> "请选择期望生效日";
            case INTERVIEW -> "请选择应聘时间";
            case SEAL -> "请选择盖章日期";
            case QUALIFY -> "请选择申请时间";
            case TRIP -> "请选择出发日期";
            case LEAVE -> "请选择开始日期";
            case LOAN -> "请选择借款日期";
            case LOAN_PAY -> "请选择付款日期";
            case LOAN_REPAY -> "请选择还款日期";
            case RESIGN -> "请选择最后工作日";
        };
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

    private static String resignType(String raw) {
        String value = BizParse.required(raw, "请选择离职类型");
        String upper = value.trim().toUpperCase();
        if ("VOLUNTARY".equals(upper) || "主动离职".equals(value)) {
            return "VOLUNTARY";
        }
        if ("NEGOTIATED".equals(upper) || "协商解除".equals(value)) {
            return "NEGOTIATED";
        }
        if ("OTHER".equals(upper) || "其他".equals(value)) {
            return "OTHER";
        }
        throw ApiException.badRequest("离职类型只能是主动离职 / 协商解除 / 其他");
    }

    private static String resignTypeLabel(String type) {
        if ("VOLUNTARY".equals(type)) {
            return "主动离职";
        }
        if ("NEGOTIATED".equals(type)) {
            return "协商解除";
        }
        if ("OTHER".equals(type)) {
            return "其他";
        }
        return type;
    }

    private static boolean writable(Map<String, String> modes, String field) {
        if (modes == null || modes.isEmpty()) {
            return true;
        }
        return WorkflowService.EDITABLE.equals(modes.getOrDefault(field, WorkflowService.EDITABLE));
    }
}
