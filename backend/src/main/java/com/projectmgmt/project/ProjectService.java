package com.projectmgmt.project;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.LinkGuard;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.common.PublicFilters;
import com.projectmgmt.customer.Customer;
import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.contract.Contract;
import com.projectmgmt.contract.ContractRepository;
import com.projectmgmt.contract.ContractStatus;
import com.projectmgmt.payment.PaymentRepository;
import com.projectmgmt.profile.FileStorageService;
import com.projectmgmt.region.Region;
import com.projectmgmt.region.RegionRepository;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.user.UserStatus;
import com.projectmgmt.role.DataScopeQuery;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.workflow.WorkflowService;
import jakarta.persistence.criteria.JoinType;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final CustomerRepository customerRepository;
    private final ContractRepository contractRepository;
    private final RegionRepository regionRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final WorkflowService workflowService;
    private final PermissionService permissionService;
    private final PaymentRepository paymentRepository;
    private final ProjectProgressService projectProgressService;

    @Transactional(readOnly = true)
    public PageResult<ProjectView> list(
            String keyword,
            String publicFilter,
            Long customerId,
            Long contractId,
            Boolean linkable,
            Long keepId,
            int page,
            int size
    ) {
        Specification<Project> spec = (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return cb.conjunction();
            }
            String like = "%" + keyword.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("code")), like),
                    cb.like(cb.lower(root.get("companyName")), like)
            );
        };
        if (customerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId));
        }
        if (contractId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("contract").get("id"), contractId));
        }
        if (Boolean.TRUE.equals(linkable)) {
            spec = spec.and((root, query, cb) -> {
                var contract = root.join("contract", JoinType.LEFT);
                var ok = cb.and(
                        cb.notEqual(root.get("status"), ProjectStatus.VOIDED),
                        cb.or(
                                cb.isNull(root.get("contract")),
                                cb.notEqual(contract.get("status"), ContractStatus.VOIDED)
                        )
                );
                if (keepId != null) {
                    return cb.or(ok, cb.equal(root.get("id"), keepId));
                }
                return ok;
            });
        }
        spec = spec.and(PublicFilters.byFlag(publicFilter));
        spec = spec.and(DataScopeQuery.createdBy(permissionService.ownFilterUserId(FormModule.PROJECT)));
        Page<Project> result = projectRepository.findAll(spec, PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id")));
        return new PageResult<>(result.map(ProjectView::from).getContent(), result.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public ProjectView get(Long id) {
        Project project = projectRepository.findById(id).orElseThrow(() -> ApiException.notFound("项目不存在"));
        permissionService.requireOwnRecord(FormModule.PROJECT, project.getCreatedBy());
        project.getCustomer().getCompanyName();
        if (project.getContract() != null) {
            project.getContract().getName();
        }
        project.getManager().getDisplayName();
        project.getRegion().getName();
        project.getAttachments().size();
        if (project.getRegionLeader() != null) {
            project.getRegionLeader().getDisplayName();
        }
        return ProjectView.from(project);
    }

    @Transactional
    public ProjectView create(
            String name,
            String code,
            Long customerId,
            Long contractId,
            Long managerId,
            Long regionId,
            String overview,
            Boolean isPublic,
            SysUser creator,
            List<MultipartFile> files
    ) {
        if (!StringUtils.hasText(name)) {
            throw ApiException.badRequest("请填写项目名称");
        }
        if (!StringUtils.hasText(code)) {
            throw ApiException.badRequest("请填写项目编码");
        }
        String trimmedCode = code.trim();
        if (projectRepository.existsByCode(trimmedCode)) {
            throw ApiException.badRequest("项目编码已存在");
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.badRequest("请选择客户"));
        SysUser manager = requireEnabledUser(managerId, "请选择项目经理");
        Region region = regionRepository.findById(regionId)
                .orElseThrow(() -> ApiException.badRequest("请选择项目所属区域"));

        Project project = new Project();
        project.setName(name.trim());
        project.setCode(trimmedCode);
        project.setCustomer(customer);
        project.setContract(resolveContract(contractId, customer, null));
        project.setCompanyName(customer.getCompanyName());
        project.setCompanyCode(customer.getCode());
        project.setCompanyLevel(customer.getLevel());
        project.setManager(manager);
        project.setRegion(region);
        project.setRegionLeader(region.getLeader());
        project.setOverview(overview == null ? "" : overview.trim());
        project.setStatus(ProjectStatus.INITIATED);
        project.setCreatedBy(creator);
        if (isPublic != null) {
            project.setPublic(isPublic);
        }

        if (files != null) {
            attachFiles(project, files);
        }
        Project saved = projectRepository.save(project);
        workflowService.start(FormModule.PROJECT, saved.getId());
        return ProjectView.from(saved);
    }

    @Transactional
    public ProjectView addFiles(Long id, List<MultipartFile> files) {
        Project project = projectRepository.findById(id).orElseThrow(() -> ApiException.notFound("项目不存在"));
        permissionService.requireOwnRecord(FormModule.PROJECT, project.getCreatedBy());
        attachFiles(project, files);
        return ProjectView.from(projectRepository.save(project));
    }

    @Transactional
    public ProjectView removeFile(Long id, Long fileId) {
        Project project = projectRepository.findById(id).orElseThrow(() -> ApiException.notFound("项目不存在"));
        permissionService.requireOwnRecord(FormModule.PROJECT, project.getCreatedBy());
        project.getAttachments().removeIf(item -> fileId.equals(item.getId()));
        return ProjectView.from(project);
    }

    @Transactional
    public ProjectView update(
            Long id,
            String name,
            String code,
            Long customerId,
            Long contractId,
            Long managerId,
            Long regionId,
            String overview,
            Boolean isPublic,
            List<MultipartFile> files
    ) {
        Project project = projectRepository.findById(id).orElseThrow(() -> ApiException.notFound("项目不存在"));
        permissionService.requireOwnRecord(FormModule.PROJECT, project.getCreatedBy());
        Map<String, String> modes = workflowService.writeModes(FormModule.PROJECT, id);
        if (writable(modes, "name")) {
            if (!StringUtils.hasText(name)) {
                throw ApiException.badRequest("请填写项目名称");
            }
            project.setName(name.trim());
        }
        if (writable(modes, "code")) {
            if (!StringUtils.hasText(code)) {
                throw ApiException.badRequest("请填写项目编码");
            }
            String trimmedCode = code.trim();
            if (projectRepository.existsByCodeAndIdNot(trimmedCode, id)) {
                throw ApiException.badRequest("项目编码已存在");
            }
            project.setCode(trimmedCode);
        }
        if (writable(modes, "customerId") && customerId != null) {
            Customer customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> ApiException.badRequest("请选择客户"));
            project.setCustomer(customer);
            project.setCompanyName(customer.getCompanyName());
            project.setCompanyCode(customer.getCode());
            project.setCompanyLevel(customer.getLevel());
        }
        if (writable(modes, "contractId")) {
            Long keepId = project.getContract() == null ? null : project.getContract().getId();
            project.setContract(resolveContract(contractId, project.getCustomer(), keepId));
        }
        if (writable(modes, "managerId") && managerId != null) {
            project.setManager(requireEnabledUser(managerId, "请选择项目经理"));
        }
        if (writable(modes, "regionId") && regionId != null) {
            Region region = regionRepository.findById(regionId)
                    .orElseThrow(() -> ApiException.badRequest("请选择项目所属区域"));
            project.setRegion(region);
            project.setRegionLeader(region.getLeader());
        }
        if (writable(modes, "overview")) {
            project.setOverview(overview == null ? "" : overview.trim());
        }
        if (isPublic != null) {
            project.setPublic(isPublic);
        }
        if (writable(modes, "attachments") && files != null) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                FileStorageService.StoredFile stored = fileStorageService.saveProjectFile(file);
                ProjectAttachment attachment = new ProjectAttachment();
                attachment.setProject(project);
                attachment.setOriginalName(stored.originalName());
                attachment.setUrl(stored.url());
                attachment.setSize(stored.size());
                attachment.setContentType(stored.contentType());
                project.getAttachments().add(attachment);
            }
        }
        return ProjectView.from(project);
    }

    @Transactional
    public void delete(Long id) {
        Project project = projectRepository.findById(id).orElseThrow(() -> ApiException.notFound("项目不存在"));
        permissionService.requireOwnRecord(FormModule.PROJECT, project.getCreatedBy());
        if (paymentRepository.existsByProject_Id(id)) {
            throw ApiException.badRequest("该项目仍有付款申请，请先删除后再删项目");
        }
        workflowService.deleteForBiz(FormModule.PROJECT, id);
        projectProgressService.deleteForProject(project);
        projectRepository.delete(project);
    }

    @Transactional
    public ProjectView voidProject(Long id) {
        Project project = projectRepository.findById(id).orElseThrow(() -> ApiException.notFound("项目不存在"));
        permissionService.requireOwnRecord(FormModule.PROJECT, project.getCreatedBy());
        project.setStatus(ProjectStatus.VOIDED);
        return ProjectView.from(project);
    }

    @Transactional
    public ProjectView restore(Long id) {
        Project project = projectRepository.findById(id).orElseThrow(() -> ApiException.notFound("项目不存在"));
        permissionService.requireOwnRecord(FormModule.PROJECT, project.getCreatedBy());
        project.setStatus(ProjectStatus.INITIATED);
        return ProjectView.from(project);
    }

    private Contract resolveContract(Long contractId, Customer customer, Long keepId) {
        if (contractId == null) {
            return null;
        }
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> ApiException.badRequest("请选择合同"));
        if (customer != null && contract.getCustomer() != null && !contract.getCustomer().getId().equals(customer.getId())) {
            throw ApiException.badRequest("合同必须属于所选客户");
        }
        LinkGuard.assertNewContractLink(contract, keepId);
        return contract;
    }

    private boolean writable(Map<String, String> modes, String field) {
        return WorkflowService.EDITABLE.equals(modes.getOrDefault(field, WorkflowService.EDITABLE));
    }

    private void attachFiles(Project project, List<MultipartFile> files) {
        if (files == null) {
            return;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            FileStorageService.StoredFile stored = fileStorageService.saveProjectFile(file);
            ProjectAttachment attachment = new ProjectAttachment();
            attachment.setProject(project);
            attachment.setOriginalName(stored.originalName());
            attachment.setUrl(stored.url());
            attachment.setSize(stored.size());
            attachment.setContentType(stored.contentType());
            project.getAttachments().add(attachment);
        }
    }

    private SysUser requireEnabledUser(Long id, String message) {
        SysUser user = userRepository.findById(id).orElseThrow(() -> ApiException.badRequest(message));
        if (user.getStatus() != UserStatus.ENABLED) {
            throw ApiException.badRequest("所选人员已停用");
        }
        return user;
    }
}
