package com.projectmgmt.bid;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.BizParse;
import com.projectmgmt.common.LinkGuard;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.customer.Customer;
import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.profile.FileStorageService;
import com.projectmgmt.project.Project;
import com.projectmgmt.project.ProjectRepository;
import com.projectmgmt.role.DataScopeQuery;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.workflow.WorkflowInstance;
import com.projectmgmt.workflow.WorkflowInstanceRepository;
import com.projectmgmt.workflow.WorkflowService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
public class BidService {

    private static final FormModule MODULE = FormModule.BIDDING;

    private final BidRepository bidRepository;
    private final CustomerRepository customerRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final WorkflowService workflowService;
    private final WorkflowInstanceRepository instanceRepository;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public PageResult<BidView> list(String keyword, int page, int size) {
        Specification<Bid> spec = (root, query, cb) -> cb.conjunction();
        if (StringUtils.hasText(keyword)) {
            spec = spec.and((root, query, cb) -> {
                if (query != null) {
                    query.distinct(true);
                }
                String like = "%" + keyword.trim().toLowerCase() + "%";
                Join<Bid, Project> project = root.join("project", JoinType.INNER);
                Join<Bid, Customer> customer = root.join("customer", JoinType.INNER);
                List<Predicate> ors = new ArrayList<>();
                ors.add(cb.like(cb.lower(root.get("code")), like));
                ors.add(cb.like(cb.lower(root.get("tenderName")), like));
                ors.add(cb.like(cb.lower(root.get("tenderCode")), like));
                ors.add(cb.like(cb.lower(project.get("name")), like));
                ors.add(cb.like(cb.lower(project.get("code")), like));
                ors.add(cb.like(cb.lower(customer.get("shortName")), like));
                ors.add(cb.like(cb.lower(customer.get("companyName")), like));
                return cb.or(ors.toArray(Predicate[]::new));
            });
        }
        spec = spec.and(DataScopeQuery.createdBy(permissionService.ownFilterUserId(MODULE)));
        Page<Bid> result = bidRepository.findAll(
                spec,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id"))
        );
        return new PageResult<>(result.getContent().stream().map(this::toView).toList(), result.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public BidView get(Long id) {
        Bid item = require(id);
        permissionService.requireOwnRecord(MODULE, item.getCreatedBy());
        item.getFiles().size();
        item.getSignups().size();
        return toView(item);
    }

    @Transactional
    public BidView create(UpsertBidRequest request, SysUser creator) {
        if (!workflowService.hasPublished(MODULE)) {
            throw ApiException.badRequest("请先在流程引擎发布招标信息流程");
        }
        Bid item = new Bid();
        item.setCode(nextCode());
        item.setCreatedBy(creator);
        applyCore(item, request, Map.of());
        Bid saved = bidRepository.save(item);
        workflowService.start(MODULE, saved.getId());
        return toView(saved);
    }

    @Transactional
    public BidView update(Long id, UpsertBidRequest request) {
        Bid item = require(id);
        permissionService.requireOwnRecord(MODULE, item.getCreatedBy());
        applyCore(item, request, workflowService.writeModes(MODULE, id));
        return toView(item);
    }

    @Transactional
    public void delete(Long id) {
        Bid item = require(id);
        permissionService.requireOwnRecord(MODULE, item.getCreatedBy());
        workflowService.deleteForBiz(MODULE, id);
        bidRepository.delete(item);
    }

    @Transactional
    public BidView addFiles(Long id, BidFileCategory category, List<MultipartFile> files) {
        Bid item = require(id);
        permissionService.requireOwnRecord(MODULE, item.getCreatedBy());
        if (files == null) {
            return toView(item);
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            FileStorageService.StoredFile stored = fileStorageService.saveBidFile(file);
            BidFile attachment = new BidFile();
            attachment.setBid(item);
            attachment.setCategory(category);
            attachment.setOriginalName(stored.originalName());
            attachment.setUrl(stored.url());
            attachment.setSize(stored.size());
            attachment.setContentType(stored.contentType());
            item.getFiles().add(attachment);
        }
        return toView(item);
    }

    @Transactional
    public BidView removeFile(Long id, Long fileId) {
        Bid item = require(id);
        permissionService.requireOwnRecord(MODULE, item.getCreatedBy());
        item.getFiles().removeIf(file -> file.getId() != null && file.getId().equals(fileId));
        return toView(item);
    }

    private void applyCore(Bid item, UpsertBidRequest request, Map<String, String> modes) {
        if (writable(modes, "customerId") || item.getCustomer() == null) {
            item.setCustomer(requireCustomer(request.customerId()));
        }
        if (writable(modes, "salesLeadId") || item.getSalesLead() == null) {
            item.setSalesLead(optionalUser(request.salesLeadId()));
        }
        if (writable(modes, "projectId") || item.getProject() == null) {
            Project project = requireProject(request.projectId(), previousProjectId(item));
            if (!project.getCustomer().getId().equals(item.getCustomer().getId())) {
                throw ApiException.badRequest("项目须属于所选客户");
            }
            item.setProject(project);
        }
        if (writable(modes, "opportunityName") || item.getOpportunityName() == null) {
            item.setOpportunityName(BizParse.blankToNull(request.opportunityName()));
        }
        if (writable(modes, "bidAgent") || item.getBidAgent() == null) {
            item.setBidAgent(BizParse.blankToNull(request.bidAgent()));
        }
        if (writable(modes, "tenderName") || item.getTenderName() == null) {
            item.setTenderName(BizParse.required(request.tenderName(), "请填写招标文件项目名称"));
        }
        if (writable(modes, "tenderCode") || item.getTenderCode() == null) {
            item.setTenderCode(BizParse.required(request.tenderCode(), "请填写招标编号"));
        }
        if (writable(modes, "downloadDeadline") || item.getDownloadDeadline() == null) {
            item.setDownloadDeadline(parseDateTime(request.downloadDeadline(), "请选择标书投递截止时间"));
        }
        if (writable(modes, "bidTime") || item.getBidTime() == null) {
            item.setBidTime(parseDateTime(request.bidTime(), "请选择开标时间"));
        }
        if (writable(modes, "description") || item.getDescription() == null) {
            item.setDescription(BizParse.blankToNull(request.description()));
        }
        if (writable(modes, "announcementUrl") || item.getAnnouncementUrl() == null) {
            item.setAnnouncementUrl(BizParse.blankToNull(request.announcementUrl()));
        }
        if (writable(modes, "signedUp")) {
            item.setSignedUp(Boolean.TRUE.equals(request.signedUp()));
        }
        if (writable(modes, "result") || item.getResult() == null) {
            item.setResult(parseResult(request.result(), true, "请选择最终结果"));
        }
        if (writable(modes, "winAmount") || item.getWinAmount() == null) {
            item.setWinAmount(optionalAmount(request.winAmount(), item.getResult()));
        }
        if (writable(modes, "winUnit") || item.getWinUnit() == null) {
            item.setWinUnit(BizParse.blankToNull(request.winUnit()));
        }
        if (writable(modes, "winUnitName") || item.getWinUnitName() == null) {
            item.setWinUnitName(BizParse.blankToNull(request.winUnitName()));
        }
        if (writable(modes, "round2Name") || item.getRound2Name() == null) {
            item.setRound2Name(BizParse.blankToNull(request.round2Name()));
        }
        if (writable(modes, "round2DownloadDeadline") || item.getRound2DownloadDeadline() == null) {
            item.setRound2DownloadDeadline(parseDateTimeOptional(request.round2DownloadDeadline()));
        }
        if (writable(modes, "round2BidTime") || item.getRound2BidTime() == null) {
            item.setRound2BidTime(parseDateTimeOptional(request.round2BidTime()));
        }
        if (writable(modes, "round2AnnouncementUrl") || item.getRound2AnnouncementUrl() == null) {
            item.setRound2AnnouncementUrl(BizParse.blankToNull(request.round2AnnouncementUrl()));
        }
        if (writable(modes, "round2Result") || item.getRound2Result() == null) {
            item.setRound2Result(parseResult(request.round2Result(), false, null));
        }
        if (writable(modes, "round2WinAmount") || item.getRound2WinAmount() == null) {
            item.setRound2WinAmount(optionalAmount(request.round2WinAmount(), item.getRound2Result()));
        }
        if (writable(modes, "round2WinUnit") || item.getRound2WinUnit() == null) {
            item.setRound2WinUnit(BizParse.blankToNull(request.round2WinUnit()));
        }
        if (writable(modes, "round2WinUnitName") || item.getRound2WinUnitName() == null) {
            item.setRound2WinUnitName(BizParse.blankToNull(request.round2WinUnitName()));
        }
        if (writable(modes, "finalContractName") || item.getFinalContractName() == null) {
            item.setFinalContractName(BizParse.blankToNull(request.finalContractName()));
        }
        if (writable(modes, "signups") || item.getSignups().isEmpty()) {
            replaceSignups(item, request.signups());
        }
        if (item.isSignedUp() && item.getSignups().isEmpty()) {
            throw ApiException.badRequest("请至少填写一行报名单位");
        }
    }

    private void replaceSignups(Bid item, List<UpsertBidRequest.BidSignupLine> lines) {
        item.getSignups().clear();
        if (lines == null) {
            return;
        }
        int order = 0;
        for (UpsertBidRequest.BidSignupLine line : lines) {
            if (line == null || !StringUtils.hasText(line.companyName())) {
                continue;
            }
            BidSignup signup = new BidSignup();
            signup.setBid(item);
            signup.setCompanyName(line.companyName().trim());
            signup.setBidder(optionalUser(line.bidderId()));
            signup.setRegistrantName(BizParse.blankToNull(line.registrantName()));
            signup.setSortOrder(order++);
            item.getSignups().add(signup);
        }
    }

    private BidResult parseResult(String raw, boolean required, String message) {
        if (!StringUtils.hasText(raw)) {
            if (required) {
                throw ApiException.badRequest(message == null ? "请选择最终结果" : message);
            }
            return null;
        }
        try {
            return BidResult.parse(raw, required);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage() == null ? "最终结果不正确" : ex.getMessage());
        }
    }

    private BigDecimal optionalAmount(BigDecimal amount, BidResult result) {
        if (result == BidResult.WON) {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.badRequest("中标时请填写中标金额");
            }
            return amount.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return amount;
    }

    private Customer requireCustomer(Long customerId) {
        if (customerId == null) {
            throw ApiException.badRequest("请选择客户");
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ApiException.badRequest("客户不存在"));
        return customer;
    }

    private Project requireProject(Long projectId, Long previousId) {
        if (projectId == null) {
            throw ApiException.badRequest("请选择项目");
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.badRequest("项目不存在"));
        permissionService.requireOwnRecord(FormModule.PROJECT, project.getCreatedBy());
        LinkGuard.assertNewProjectLink(project, previousId);
        return project;
    }

    private SysUser optionalUser(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).orElseThrow(() -> ApiException.badRequest("成员不存在"));
    }

    private Long previousProjectId(Bid item) {
        return item.getProject() == null ? null : item.getProject().getId();
    }

    private Bid require(Long id) {
        return bidRepository.findById(id).orElseThrow(() -> ApiException.notFound("招标信息不存在"));
    }

    private String nextCode() {
        String prefix = "ZB" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 1; i < 1000; i++) {
            String code = prefix + String.format("%03d", i);
            if (!bidRepository.existsByCode(code)) {
                return code;
            }
        }
        throw ApiException.badRequest("当日招标信息编号已用尽");
    }

    private boolean writable(Map<String, String> modes, String key) {
        if (modes == null || modes.isEmpty()) {
            return true;
        }
        String mode = modes.get(key);
        return mode == null || "EDITABLE".equals(mode);
    }

    private LocalDateTime parseDateTime(String raw, String message) {
        LocalDateTime value = parseDateTimeOptional(raw);
        if (value == null) {
            throw ApiException.badRequest(message);
        }
        return value;
    }

    private LocalDateTime parseDateTimeOptional(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim().replace(' ', 'T');
        try {
            if (value.length() == 10) {
                return LocalDate.parse(value).atStartOfDay();
            }
            if (value.length() == 16) {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
            }
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest("时间格式不正确");
        }
    }

    private BidView toView(Bid item) {
        Customer customer = item.getCustomer();
        Project project = item.getProject();
        SysUser sales = item.getSalesLead();
        WorkflowInstance instance = instanceRepository.findByFormModuleAndBizId(MODULE, item.getId()).orElse(null);
        String status = instance == null ? null : instance.getStatus();
        List<BidView.BidFileView> screenshots = new ArrayList<>();
        List<BidView.BidFileView> awardFiles = new ArrayList<>();
        List<BidView.BidFileView> round2AwardFiles = new ArrayList<>();
        for (BidFile file : item.getFiles()) {
            BidView.BidFileView view = new BidView.BidFileView(
                    file.getId(),
                    file.getCategory().name(),
                    file.getOriginalName(),
                    file.getUrl(),
                    file.getSize(),
                    file.getContentType()
            );
            if (file.getCategory() == BidFileCategory.AWARD) {
                awardFiles.add(view);
            } else if (file.getCategory() == BidFileCategory.AWARD2) {
                round2AwardFiles.add(view);
            } else {
                screenshots.add(view);
            }
        }
        return new BidView(
                item.getId(),
                item.getCode(),
                customer == null ? null : customer.getId(),
                customer == null ? null : customer.getCompanyName(),
                customer == null ? null : customer.getShortName(),
                sales == null ? null : sales.getId(),
                sales == null ? null : sales.getDisplayName(),
                project == null ? null : project.getId(),
                project == null ? null : project.getName(),
                project == null ? null : project.getCode(),
                item.getOpportunityName(),
                item.getBidAgent(),
                item.getTenderName(),
                item.getTenderCode(),
                item.getDownloadDeadline(),
                item.getBidTime(),
                item.getDescription(),
                item.getAnnouncementUrl(),
                item.isSignedUp(),
                item.getResult() == null ? null : item.getResult().name(),
                item.getResult() == null ? null : item.getResult().label(),
                item.getWinAmount(),
                item.getWinUnit(),
                item.getWinUnitName(),
                item.getRound2Name(),
                item.getRound2DownloadDeadline(),
                item.getRound2BidTime(),
                item.getRound2AnnouncementUrl(),
                item.getRound2Result() == null ? null : item.getRound2Result().name(),
                item.getRound2Result() == null ? null : item.getRound2Result().label(),
                item.getRound2WinAmount(),
                item.getRound2WinUnit(),
                item.getRound2WinUnitName(),
                item.getFinalContractName(),
                item.getSignups().stream()
                        .map(row -> new BidView.BidSignupView(
                                row.getId(),
                                row.getCompanyName(),
                                row.getBidder() == null ? null : row.getBidder().getId(),
                                row.getBidder() == null ? null : row.getBidder().getDisplayName(),
                                row.getRegistrantName()
                        ))
                        .toList(),
                screenshots,
                awardFiles,
                round2AwardFiles,
                status,
                workflowService.statusLabel(status),
                item.getCreatedAt()
        );
    }
}
