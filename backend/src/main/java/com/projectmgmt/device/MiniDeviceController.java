package com.projectmgmt.device;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.ApiResponse;
import com.projectmgmt.org.OrgDeptScope;
import com.projectmgmt.security.CurrentUser;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/mini/devices")
@RequiredArgsConstructor
public class MiniDeviceController {

    private final DeviceRepository deviceRepository;
    private final DeviceWorkOrderService workOrderService;
    private final DeviceMaintService deviceMaintService;
    private final OrgDeptScope orgDeptScope;
    private final CurrentUser currentUser;

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<MiniDeviceHome> home() {
        List<DeviceView> devices = visibleDevices();
        return ApiResponse.ok(new MiniDeviceHome(devices.size(), orgDeptScope.companyName(), devices));
    }

    @GetMapping("/lookup")
    @Transactional(readOnly = true)
    public ApiResponse<DeviceView> lookup(@RequestParam String code) {
        if (code == null || code.isBlank()) {
            throw ApiException.badRequest("请扫描设备二维码");
        }
        Device device = deviceRepository.findByCode(code.trim()).orElseThrow(() -> ApiException.notFound("找不到这台设备"));
        orgDeptScope.assertInCompany(device.getDepartment());
        return ApiResponse.ok(DeviceView.from(device));
    }

    @GetMapping("/{id:\\d+}")
    @Transactional(readOnly = true)
    public ApiResponse<MiniDeviceDetail> detail(@PathVariable Long id) {
        Device device = requireCompanyDevice(id);
        return ApiResponse.ok(new MiniDeviceDetail(
                DeviceView.from(device),
                workOrderService.listForDevice(device.getId()),
                deviceMaintService.briefsForDevice(device.getCode())));
    }

    @PostMapping("/{id}/repairs")
    public ApiResponse<DeviceWorkOrderView> repair(@PathVariable Long id, @RequestBody ScanRepairRequest request) {
        return ApiResponse.ok(workOrderService.createCompanyRepair(id, request, currentUser.entity()));
    }

    @PostMapping("/{deviceId:\\d+}/repairs/{orderId:\\d+}/files")
    public ApiResponse<Void> repairFiles(
            @PathVariable Long deviceId,
            @PathVariable Long orderId,
            @RequestParam("files") List<MultipartFile> files) {
        workOrderService.addCompanyRepairFiles(deviceId, orderId, files, currentUser.entity());
        return ApiResponse.ok(null);
    }

    private List<DeviceView> visibleDevices() {
        Set<Long> companyIds = orgDeptScope.companyDepartmentIds();
        return deviceRepository.findAll().stream()
                .filter(device -> device.getDepartment() != null)
                .filter(device -> companyIds == null || companyIds.contains(device.getDepartment().getId()))
                .sorted(Comparator.comparing(Device::getCode, Comparator.nullsLast(String::compareTo)))
                .map(DeviceView::from)
                .toList();
    }

    private Device requireCompanyDevice(Long id) {
        Device device = deviceRepository.findById(id).orElseThrow(() -> ApiException.notFound("设备不存在"));
        orgDeptScope.assertInCompany(device.getDepartment());
        return device;
    }

    public record MiniDeviceHome(long total, String companyName, List<DeviceView> devices) {}

    public record MiniDeviceDetail(
            DeviceView device,
            List<DeviceWorkOrderView> orders,
            List<DeviceMaintService.DeviceBrief> maintRecords) {}

}
