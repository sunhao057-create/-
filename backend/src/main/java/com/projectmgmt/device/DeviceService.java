package com.projectmgmt.device;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.BizParse;
import com.projectmgmt.org.OrgDepartment;
import com.projectmgmt.org.OrgDeptScope;
import com.projectmgmt.org.OrgService;
import com.projectmgmt.profile.FileStorageService;
import com.projectmgmt.role.DataScopeQuery;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import jakarta.persistence.criteria.JoinType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final DeviceWorkOrderRepository workOrderRepository;
    private final OrgService orgService;
    private final OrgDeptScope orgDeptScope;
    private final PermissionService permissionService;
    private final FileStorageService fileStorageService;
    private final DeviceQrCode deviceQrCode;

    @Transactional(readOnly = true)
    public List<DeviceView> list(String keyword) {
        return list(DeviceQuery.keywordOnly(keyword));
    }

    @Transactional(readOnly = true)
    public List<DeviceView> list(DeviceQuery query) {
        return list(query, null);
    }

    @Transactional(readOnly = true)
    public List<DeviceView> list(DeviceQuery query, java.util.Collection<Long> ids) {
        DeviceQuery q = query == null ? DeviceQuery.keywordOnly(null) : query;
        Specification<Device> spec = orgDeptScope.<Device>departmentIn("department")
                .and(DataScopeQuery.createdBy(permissionService.ownFilterUserId(FormModule.DEVICE)));
        spec = spec.and((root, cq, cb) -> {
            if (cq != null) {
                cq.distinct(true);
                if (Device.class.equals(cq.getResultType())) {
                    root.fetch("department", JoinType.LEFT);
                }
            }
            return cb.conjunction();
        });
        if (ids != null && !ids.isEmpty()) {
            spec = spec.and((root, cq, cb) -> root.get("id").in(ids));
        }
        if (q.departmentId() != null) {
            orgDeptScope.assertVisible(orgService.require(q.departmentId()));
            java.util.Set<Long> wanted = orgDeptScope.descendantsIncluding(q.departmentId());
            spec = spec.and((root, cq, cb) -> root.get("department").get("id").in(wanted));
        }
        if (q.enabled() != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("enabled"), q.enabled()));
        }
        spec = like(spec, "code", q.code());
        spec = like(spec, "name", q.name());
        spec = like(spec, "model", q.model());
        spec = like(spec, "location", q.location());
        if (StringUtils.hasText(q.keyword())) {
            String like = "%" + q.keyword().trim().toLowerCase() + "%";
            spec = spec.and((root, cq, cb) -> cb.or(
                    cb.like(cb.lower(root.get("code")), like),
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("model")), like),
                    cb.like(cb.lower(root.get("serialNo")), like),
                    cb.like(cb.lower(root.get("location")), like)
            ));
        }
        return deviceRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(DeviceView::from)
                .toList();
    }

    private Specification<Device> like(Specification<Device> spec, String field, String value) {
        if (!StringUtils.hasText(value)) {
            return spec;
        }
        String pattern = "%" + value.trim().toLowerCase() + "%";
        return spec.and((root, cq, cb) -> cb.like(cb.lower(cb.coalesce(root.get(field), "")), pattern));
    }

    @Transactional(readOnly = true)
    public DeviceView get(Long id) {
        return DeviceView.from(requireVisible(id));
    }

    @Transactional
    public DeviceView create(UpsertDeviceRequest request, SysUser actor) {
        Device device = new Device();
        device.setCreatedBy(actor);
        device.setCode(nextCode());
        device.setStatus(DeviceStatus.IN_USE);
        apply(device, request, true);
        return DeviceView.from(deviceRepository.save(device));
    }

    @Transactional
    public DeviceView update(Long id, UpsertDeviceRequest request) {
        Device device = requireVisible(id);
        permissionService.requireOwnRecord(FormModule.DEVICE, device.getCreatedBy());
        if (device.getStatus() == DeviceStatus.SCRAPPED) {
            throw ApiException.badRequest("已报废设备不能修改");
        }
        apply(device, request, false);
        return DeviceView.from(device);
    }

    @Transactional
    public void delete(Long id) {
        Device device = requireVisible(id);
        permissionService.requireOwnRecord(FormModule.DEVICE, device.getCreatedBy());
        if (workOrderRepository.existsByDevice_Id(id)) {
            throw ApiException.badRequest("该设备已有工单，不能删除");
        }
        deviceRepository.delete(device);
    }

    @Transactional
    public DeviceView updatePhoto(Long id, MultipartFile photo) {
        Device device = requireVisible(id);
        permissionService.requireOwnRecord(FormModule.DEVICE, device.getCreatedBy());
        if (device.getStatus() == DeviceStatus.SCRAPPED) {
            throw ApiException.badRequest("已报废设备不能修改");
        }
        device.setPhotoUrl(fileStorageService.saveDevicePhoto(photo));
        return DeviceView.from(device);
    }

    @Transactional
    public DeviceView clearPhoto(Long id) {
        Device device = requireVisible(id);
        permissionService.requireOwnRecord(FormModule.DEVICE, device.getCreatedBy());
        device.setPhotoUrl(null);
        return DeviceView.from(device);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> qrcode(Long id) {
        Device device = requireVisible(id);
        byte[] png = deviceQrCode.png(device, 360);
        String filename = device.getCode() + "-二维码.png";
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, java.nio.charset.StandardCharsets.UTF_8).build().toString()
                )
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    public Device requireVisible(Long id) {
        Device device = require(id);
        orgDeptScope.assertVisible(device.getDepartment());
        permissionService.requireOwnRecord(FormModule.DEVICE, device.getCreatedBy());
        return device;
    }

    public Device require(Long id) {
        if (id == null) {
            throw ApiException.badRequest("请选择设备");
        }
        return deviceRepository.findById(id).orElseThrow(() -> ApiException.notFound("设备不存在"));
    }

    private void apply(Device device, UpsertDeviceRequest request, boolean creating) {
        device.setName(BizParse.required(request == null ? null : request.name(), "请填写设备名称"));
        device.setModel(BizParse.blankToNull(request == null ? null : request.model()));
        device.setBrand(BizParse.blankToNull(request == null ? null : request.brand()));
        device.setSerialNo(BizParse.blankToNull(request == null ? null : request.serialNo()));
        device.setLocation(BizParse.required(request == null ? null : request.location(), "请填写放置位置"));
        device.setRemark(BizParse.blankToNull(request == null ? null : request.remark()));
        OrgDepartment department = orgService.require(request == null ? null : request.departmentId());
        orgDeptScope.assertVisible(department);
        device.setDepartment(department);
        Long managerId = request == null ? null : request.managerId();
        if (managerId == null) {
            device.setManager(null);
        } else {
            device.setManager(userRepository.findById(managerId).orElseThrow(() -> ApiException.badRequest("客户经理不存在")));
        }
        if (creating || device.getStatus() != DeviceStatus.SCRAPPED) {
            device.setEnabled(request == null || request.enabled() == null || Boolean.TRUE.equals(request.enabled()));
        }
    }

    private String nextCode() {
        String prefix = "EQ" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 1; i < 1000; i++) {
            String code = prefix + String.format("%03d", i);
            if (!deviceRepository.existsByCode(code)) {
                return code;
            }
        }
        throw ApiException.badRequest("当日设备编号过多，请稍后重试");
    }
}
