package com.projectmgmt.device;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.customer.Customer;
import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.common.BizParse;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.org.OrgDepartment;
import com.projectmgmt.org.OrgDeptScope;
import com.projectmgmt.org.OrgService;
import com.projectmgmt.profile.FileStorageService;
import com.projectmgmt.project.AttachmentView;
import com.projectmgmt.role.DataScopeQuery;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.workflow.WorkflowInstance;
import com.projectmgmt.workflow.WorkflowInstanceRepository;
import com.projectmgmt.workflow.WorkflowService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class DeviceWorkOrderService {

    private final DeviceWorkOrderRepository workOrderRepository;
    private final DeviceRepository deviceRepository;
    private final CustomerRepository customerRepository;
    private final DeviceService deviceService;
    private final OrgService orgService;
    private final OrgDeptScope orgDeptScope;
    private final WorkflowService workflowService;
    private final WorkflowInstanceRepository instanceRepository;
    private final PermissionService permissionService;
    private final DeviceSettleService settleService;
    private final FileStorageService fileStorageService;

    /** 本公司设备上的工单，供小程序查看，不走电脑端的模块权限。 */
    @Transactional(readOnly = true)
    public List<DeviceWorkOrderView> listForCompany(DeviceWorkOrderKind kind) {
        Set<Long> companyIds = orgDeptScope.companyDepartmentIds();
        return workOrderRepository.findAll().stream()
                .filter(order -> order.getKind() == kind)
                .filter(order -> order.getDevice() != null && order.getDevice().getDepartment() != null)
                .filter(order -> companyIds == null || companyIds.contains(order.getDevice().getDepartment().getId()))
                .sorted(Comparator.comparing(DeviceWorkOrder::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(order -> toView(order, kind))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResult<DeviceWorkOrderView> list(DeviceWorkOrderKind kind, String keyword, int page, int size) {
        FormModule module = moduleOf(kind);
        Specification<DeviceWorkOrder> spec = (root, query, cb) -> cb.equal(root.get("kind"), kind);
        spec = spec.and(orgDeptScope.<DeviceWorkOrder>deviceDepartmentIn())
                .and(DataScopeQuery.createdBy(permissionService.ownFilterUserId(module)));
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> {
                if (query != null) {
                    query.distinct(true);
                }
                Join<DeviceWorkOrder, Device> device = root.join("device", JoinType.INNER);
                List<Predicate> ors = new ArrayList<>();
                ors.add(cb.like(cb.lower(root.get("code")), like));
                ors.add(cb.like(cb.lower(root.get("reason")), like));
                ors.add(cb.like(cb.lower(device.get("name")), like));
                ors.add(cb.like(cb.lower(device.get("code")), like));
                return cb.or(ors.toArray(Predicate[]::new));
            });
        }
        Page<DeviceWorkOrder> result = workOrderRepository.findAll(
                spec,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id"))
        );
        return new PageResult<>(result.map(item -> toView(item, kind)).getContent(), result.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public DeviceWorkOrderView get(DeviceWorkOrderKind kind, Long id) {
        return toView(requireVisible(kind, id), kind);
    }

    @Transactional(readOnly = true)
    public List<DeviceWorkOrderView> listForDevice(Long deviceId) {
        return workOrderRepository.findByDevice_IdOrderByIdDesc(deviceId).stream()
                .map(order -> toView(order, order.getKind()))
                .toList();
    }

    /** 本公司人员在小程序扫码报修，不要求设备维修模块的新增权限。 */
    @Transactional
    public DeviceWorkOrderView createCompanyRepair(Long deviceId, ScanRepairRequest request, SysUser creator) {
        FormModule module = FormModule.DEVICE_REPAIR;
        if (!workflowService.hasPublished(module)) {
            throw ApiException.badRequest("请先在流程引擎发布" + module.label() + "流程");
        }
        Device device = deviceRepository.findById(deviceId).orElseThrow(() -> ApiException.notFound("设备不存在"));
        orgDeptScope.assertInCompany(device.getDepartment());
        if (workOrderRepository.existsByDevice_IdAndAppliedFalse(device.getId())) {
            throw ApiException.badRequest("该设备已有未完成工单");
        }
        if (device.getStatus() == DeviceStatus.SCRAPPED) {
            throw ApiException.badRequest("已报废设备不能再发起工单");
        }
        if (device.getStatus() != DeviceStatus.IN_USE) {
            throw ApiException.badRequest("设备当前状态为" + DeviceLabels.status(device.getStatus()) + "，不能发起维修");
        }
        ScanRepairRequest filled = fillFromDevice(device, request);
        requireIntake(filled);
        DeviceWorkOrder order = new DeviceWorkOrder();
        order.setCode(nextCode(DeviceWorkOrderKind.REPAIR));
        order.setKind(DeviceWorkOrderKind.REPAIR);
        order.setCreatedBy(creator);
        order.setDevice(device);
        order.setReason(filled.fault().trim());
        order.setApplyDate(java.time.LocalDate.now());
        order.setApplied(false);
        order.setIntakeJson(JsonMapper.builder().build().writeValueAsString(filled));
        attachNamedCustomers(order, filled.clientOrg(), filled.customerName());
        lockDevice(order);
        DeviceWorkOrder saved = workOrderRepository.save(order);
        workflowService.start(module, saved.getId());
        workflowService.advance(module.name(), saved.getId(), null);
        return toView(saved, DeviceWorkOrderKind.REPAIR);
    }

    @Transactional
    public DeviceWorkOrderView create(DeviceWorkOrderKind kind, UpsertDeviceWorkOrderRequest request, SysUser creator) {
        FormModule module = moduleOf(kind);
        if (!workflowService.hasPublished(module)) {
            throw ApiException.badRequest("请先在流程引擎发布" + module.label() + "流程");
        }
        DeviceWorkOrder order = new DeviceWorkOrder();
        order.setCode(nextCode(kind));
        order.setKind(kind);
        order.setCreatedBy(creator);
        apply(order, kind, request, Map.of(), true);
        lockDevice(order);
        DeviceWorkOrder saved = workOrderRepository.save(order);
        workflowService.start(module, saved.getId());
        return toView(saved, kind);
    }

    @Transactional
    public DeviceWorkOrderView update(DeviceWorkOrderKind kind, Long id, UpsertDeviceWorkOrderRequest request) {
        DeviceWorkOrder order = requireVisible(kind, id);
        permissionService.requireOwnRecord(moduleOf(kind), order.getCreatedBy());
        Map<String, String> modes = workflowService.writeModes(moduleOf(kind), id);
        apply(order, kind, request, modes, false);
        return toView(order, kind);
    }

    @Transactional
    public void delete(DeviceWorkOrderKind kind, Long id) {
        DeviceWorkOrder order = requireVisible(kind, id);
        permissionService.requireOwnRecord(moduleOf(kind), order.getCreatedBy());
        settleService.restore(order);
        workflowService.deleteForBiz(moduleOf(kind), id);
        workOrderRepository.delete(order);
    }

    /** 小程序提交维修时附带照片，不要求设备维修模块的编辑权限。 */
    @Transactional
    public void addCompanyRepairFiles(Long deviceId, Long orderId, List<MultipartFile> files, SysUser actor) {
        Device device = deviceRepository.findById(deviceId).orElseThrow(() -> ApiException.notFound("设备不存在"));
        orgDeptScope.assertInCompany(device.getDepartment());
        DeviceWorkOrder order = workOrderRepository.findById(orderId).orElseThrow(() -> ApiException.notFound("工单不存在"));
        if (order.getKind() != DeviceWorkOrderKind.REPAIR || order.getDevice() == null
                || !deviceId.equals(order.getDevice().getId())) {
            throw ApiException.notFound("工单不存在");
        }
        if (actor == null || order.getCreatedBy() == null || !actor.getId().equals(order.getCreatedBy().getId())) {
            throw ApiException.forbidden("只能为自己提交的维修单上传照片");
        }
        addFiles(order, files);
    }

    @Transactional
    public DeviceWorkOrderView addFiles(DeviceWorkOrderKind kind, Long id, List<MultipartFile> files) {
        DeviceWorkOrder order = requireVisible(kind, id);
        permissionService.requireOwnRecord(moduleOf(kind), order.getCreatedBy());
        assertCanEditAttachments(kind, order);
        addFiles(order, files);
        return toView(order, kind);
    }

    @Transactional
    public DeviceWorkOrderView removeFile(DeviceWorkOrderKind kind, Long id, Long fileId) {
        DeviceWorkOrder order = requireVisible(kind, id);
        permissionService.requireOwnRecord(moduleOf(kind), order.getCreatedBy());
        assertCanEditAttachments(kind, order);
        DeviceWorkOrderFile target = order.getFiles().stream()
                .filter(item -> item.getId() != null && item.getId().equals(fileId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("附件不存在"));
        order.getFiles().remove(target);
        return toView(order, kind);
    }

    private void assertCanEditAttachments(DeviceWorkOrderKind kind, DeviceWorkOrder order) {
        Map<String, String> modes = workflowService.writeModes(moduleOf(kind), order.getId());
        if (!writable(modes, "attachments")) {
            throw ApiException.badRequest("当前流程节点不能修改附件");
        }
    }

    private void addFiles(DeviceWorkOrder order, List<MultipartFile> files) {
        if (files == null) {
            return;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            FileStorageService.StoredFile stored = fileStorageService.saveDeviceWorkOrderFile(file);
            DeviceWorkOrderFile attachment = new DeviceWorkOrderFile();
            attachment.setWorkOrder(order);
            attachment.setOriginalName(stored.originalName());
            attachment.setUrl(stored.url());
            attachment.setSize(stored.size());
            attachment.setContentType(stored.contentType());
            order.getFiles().add(attachment);
        }
    }

    private void apply(
            DeviceWorkOrder order,
            DeviceWorkOrderKind kind,
            UpsertDeviceWorkOrderRequest request,
            Map<String, String> modes,
            boolean creating
    ) {
        if (creating || writable(modes, "deviceId")) {
            Device device = deviceService.requireVisible(request == null ? null : request.deviceId());
            if (creating) {
                if (workOrderRepository.existsByDevice_IdAndAppliedFalse(device.getId())) {
                    throw ApiException.badRequest("该设备已有未完成工单");
                }
                if (device.getStatus() == DeviceStatus.SCRAPPED) {
                    throw ApiException.badRequest("已报废设备不能再发起工单");
                }
                if (device.getStatus() != DeviceStatus.IN_USE) {
                    throw ApiException.badRequest("设备当前状态为" + DeviceLabels.status(device.getStatus()) + "，不能发起工单");
                }
            }
            order.setDevice(device);
        }
        if (writable(modes, "reason") || order.getReason() == null) {
            order.setReason(BizParse.required(request == null ? null : request.reason(), "请填写事由"));
        }
        if (writable(modes, "applyDate") || order.getApplyDate() == null) {
            order.setApplyDate(BizParse.date(request == null ? null : request.applyDate(), "请选择申请日期"));
        }
        if (kind == DeviceWorkOrderKind.MOVE) {
            if (writable(modes, "targetLocation") || order.getTargetLocation() == null) {
                order.setTargetLocation(BizParse.required(request == null ? null : request.targetLocation(), "请填写搬迁后位置"));
            }
            if (writable(modes, "targetDepartmentId") && request != null && request.targetDepartmentId() != null) {
                OrgDepartment target = orgService.require(request.targetDepartmentId());
                orgDeptScope.assertVisible(target);
                order.setTargetDepartment(target);
            }
        } else {
            order.setTargetLocation(null);
            order.setTargetDepartment(null);
        }
        if (kind == DeviceWorkOrderKind.REPAIR && (creating || writable(modes, "clientCustomerId"))) {
            Long clientId = request == null ? null : request.clientCustomerId();
            if (clientId == null) {
                throw ApiException.badRequest("请选择委托客户");
            }
            Customer client = customerRepository.findById(clientId).orElseThrow(() -> ApiException.badRequest("委托客户不存在"));
            order.setClientCustomer(client);
            Long serviceId = request.serviceCustomerId();
            if (serviceId == null || serviceId.equals(clientId)) {
                order.setServiceCustomer(client);
            } else {
                order.setServiceCustomer(customerRepository.findById(serviceId).orElseThrow(() -> ApiException.badRequest("服务客户不存在")));
            }
        }
    }

    @Transactional(readOnly = true)
    public List<RepairCustomer> customers() {
        permissionService.require(FormModule.DEVICE_REPAIR, "VIEW");
        return customerRepository.findAllByOrderByIdDesc().stream()
                .map(customer -> new RepairCustomer(customer.getId(), customer.getCompanyName(), customer.getShortName()))
                .toList();
    }

    public record RepairCustomer(Long id, String companyName, String shortName) {
    }

    private void attachNamedCustomers(DeviceWorkOrder order, String clientName, String serviceName) {
        Customer client = findCustomer(clientName);
        Customer served = findCustomer(serviceName);
        if (served == null) {
            served = client;
        }
        order.setClientCustomer(client);
        order.setServiceCustomer(served);
    }

    private Customer findCustomer(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return customerRepository.findFirstByCompanyName(name.trim()).orElse(null);
    }

    private void lockDevice(DeviceWorkOrder order) {
        Device device = order.getDevice();
        order.setSnapshotStatus(device.getStatus());
        order.setSnapshotLocation(device.getLocation());
        order.setSnapshotDepartmentId(device.getDepartment() == null ? null : device.getDepartment().getId());
        order.setSnapshotEnabled(device.isEnabled());
        if (order.getKind() == DeviceWorkOrderKind.REPAIR) {
            device.setStatus(DeviceStatus.REPAIRING);
        } else if (order.getKind() == DeviceWorkOrderKind.MOVE) {
            device.setStatus(DeviceStatus.MOVING);
        }
    }

    private DeviceWorkOrder requireVisible(DeviceWorkOrderKind kind, Long id) {
        DeviceWorkOrder order = workOrderRepository.findById(id).orElseThrow(() -> ApiException.notFound("工单不存在"));
        if (order.getKind() != kind) {
            throw ApiException.notFound("工单不存在");
        }
        orgDeptScope.assertVisible(order.getDevice().getDepartment());
        permissionService.requireOwnRecord(moduleOf(kind), order.getCreatedBy());
        return order;
    }

    private DeviceWorkOrderView toView(DeviceWorkOrder order, DeviceWorkOrderKind kind) {
        WorkflowInstance instance = instanceRepository.findByFormModuleAndBizId(moduleOf(kind), order.getId()).orElse(null);
        String status = instance == null ? null : instance.getStatus();
        Device device = order.getDevice();
        ScanRepairRequest intake = intakeOf(order);
        return new DeviceWorkOrderView(
                order.getId(),
                order.getCode(),
                order.getKind().name(),
                device.getId(),
                device.getCode(),
                device.getName(),
                order.getReason(),
                order.getApplyDate(),
                order.getTargetLocation(),
                order.getTargetDepartment() == null ? null : order.getTargetDepartment().getId(),
                order.getTargetDepartment() == null ? null : order.getTargetDepartment().getName(),
                order.isApplied(),
                status,
                workflowService.statusLabel(status),
                order.getFiles() == null
                        ? List.of()
                        : order.getFiles().stream()
                                .map(file -> new AttachmentView(
                                        file.getId(),
                                        file.getOriginalName(),
                                        file.getUrl(),
                                        file.getSize(),
                                        file.getContentType()
                                ))
                                .toList(),
                order.getCreatedAt(),
                intake.grade(),
                intake.customerName(),
                intake.customerAddress(),
                intake.contactName(),
                intake.contactPhone(),
                intake.instrumentName(),
                intake.instrumentModel(),
                intake.brand(),
                intake.fault(),
                intake.clientOrg(),
                intake.accountManager(),
                intake.remark(),
                order.getClientCustomer() == null ? null : order.getClientCustomer().getId(),
                order.getClientCustomer() == null ? null : order.getClientCustomer().getCompanyName(),
                order.getServiceCustomer() == null ? null : order.getServiceCustomer().getId(),
                order.getServiceCustomer() == null ? null : order.getServiceCustomer().getCompanyName()
        );
    }

    private ScanRepairRequest fillFromDevice(Device device, ScanRepairRequest request) {
        ScanRepairRequest source = request == null
                ? new ScanRepairRequest(null, null, null, null, null, null, null, null, null, null, null, null)
                : request;
        String manager = device.getManager() == null ? null : device.getManager().getDisplayName();
        return new ScanRepairRequest(
                source.grade(),
                source.customerName(),
                source.customerAddress(),
                source.contactName(),
                source.contactPhone(),
                firstText(source.instrumentName(), device.getName()),
                firstText(source.instrumentModel(), device.getModel()),
                firstText(source.brand(), device.getBrand()),
                source.fault(),
                source.clientOrg(),
                firstText(source.accountManager(), manager),
                source.remark());
    }

    private void requireIntake(ScanRepairRequest request) {
        requireText(request.grade(), "请选择工单等级");
        requireText(request.customerName(), "请填写客户名称");
        requireText(request.customerAddress(), "请填写单位地址");
        requireText(request.contactName(), "请填写联系人");
        requireText(request.contactPhone(), "请填写联系电话");
        requireText(request.instrumentName(), "请填写仪器名称");
        requireText(request.instrumentModel(), "请填写仪器型号");
        requireText(request.brand(), "请填写仪器厂家或品牌");
        requireText(request.fault(), "请填写仪器故障现象概述");
        requireText(request.clientOrg(), "请填写委托单位");
        requireText(request.accountManager(), "请填写客户经理");
        requireText(request.remark(), "请填写备注");
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw ApiException.badRequest(message);
        }
    }

    private String firstText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred.trim() : (StringUtils.hasText(fallback) ? fallback.trim() : null);
    }

    private ScanRepairRequest intakeOf(DeviceWorkOrder order) {
        if (!StringUtils.hasText(order.getIntakeJson())) {
            return new ScanRepairRequest(null, null, null, null, null, null, null, null, null, null, null, null);
        }
        try {
            return JsonMapper.builder().build().readValue(order.getIntakeJson(), ScanRepairRequest.class);
        } catch (Exception ex) {
            return new ScanRepairRequest(null, null, null, null, null, null, null, null, null, null, null, null);
        }
    }

    private String nextCode(DeviceWorkOrderKind kind) {
        String prefix = switch (kind) {
            case REPAIR -> "WX";
            case MOVE -> "BQ";
            case SCRAP -> "BF";
        } + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 1; i < 1000; i++) {
            String code = prefix + String.format("%03d", i);
            if (!workOrderRepository.existsByCode(code)) {
                return code;
            }
        }
        throw ApiException.badRequest("当日工单过多，请稍后重试");
    }

    static FormModule moduleOf(DeviceWorkOrderKind kind) {
        return switch (kind) {
            case REPAIR -> FormModule.DEVICE_REPAIR;
            case MOVE -> FormModule.DEVICE_MOVE;
            case SCRAP -> FormModule.DEVICE_SCRAP;
        };
    }

    private static boolean writable(Map<String, String> modes, String field) {
        if (modes == null || modes.isEmpty()) {
            return true;
        }
        return WorkflowService.EDITABLE.equals(modes.getOrDefault(field, WorkflowService.EDITABLE));
    }
}
