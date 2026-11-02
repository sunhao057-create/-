package com.projectmgmt.device;

import com.projectmgmt.common.ApiResponse;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.org.OrgDepartmentView;
import com.projectmgmt.org.OrgDeptScope;
import com.projectmgmt.org.OrgService;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.security.CurrentUser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceWorkOrderService workOrderService;
    private final PermissionService permissionService;
    private final CurrentUser currentUser;
    private final OrgService orgService;
    private final OrgDeptScope orgDeptScope;
    private final DeviceExportService deviceExportService;

    @GetMapping
    public ApiResponse<List<DeviceView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String location
    ) {
        permissionService.require(FormModule.DEVICE, "VIEW");
        return ApiResponse.ok(deviceService.list(new DeviceQuery(keyword, departmentId, enabled, code, name, model, location)));
    }

    @GetMapping("/dept-options")
    public ApiResponse<List<OrgDepartmentView>> deptOptions() {
        permissionService.require(FormModule.DEVICE, "VIEW");
        java.util.Set<Long> ids = orgDeptScope.visibleDepartmentIds();
        return ApiResponse.ok(orgService.list().stream()
                .filter(item -> ids == null || ids.contains(item.id()))
                .toList());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String ids
    ) {
        permissionService.require(FormModule.DEVICE, "IMPORT_EXPORT");
        return deviceExportService.workbook(
                new DeviceQuery(keyword, departmentId, enabled, code, name, model, location),
                parseIds(ids)
        );
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> exportPost(@RequestBody(required = false) DeviceExportRequest request) {
        permissionService.require(FormModule.DEVICE, "IMPORT_EXPORT");
        DeviceExportRequest body = request == null ? new DeviceExportRequest(null, null, null, null, null, null, null, null) : request;
        return deviceExportService.workbook(
                new DeviceQuery(body.keyword(), body.departmentId(), body.enabled(), body.code(), body.name(), body.model(), body.location()),
                body.ids()
        );
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<DeviceView> get(@PathVariable Long id) {
        permissionService.require(FormModule.DEVICE, "VIEW");
        return ApiResponse.ok(deviceService.get(id));
    }

    @PostMapping
    public ApiResponse<DeviceView> create(@RequestBody UpsertDeviceRequest request) {
        permissionService.require(FormModule.DEVICE, "CREATE");
        return ApiResponse.ok(deviceService.create(request, currentUser.entity()));
    }

    @PutMapping("/{id:\\d+}")
    public ApiResponse<DeviceView> update(@PathVariable Long id, @RequestBody UpsertDeviceRequest request) {
        permissionService.require(FormModule.DEVICE, "EDIT");
        return ApiResponse.ok(deviceService.update(id, request));
    }

    @DeleteMapping("/{id:\\d+}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        permissionService.require(FormModule.DEVICE, "DELETE");
        deviceService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id:\\d+}/qrcode")
    public ResponseEntity<byte[]> qrcode(@PathVariable Long id) {
        permissionService.require(FormModule.DEVICE, "VIEW");
        return deviceService.qrcode(id);
    }

    @PostMapping("/{id:\\d+}/photo")
    public ApiResponse<DeviceView> uploadPhoto(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        String role = currentUser.entity().getRole();
        if (!permissionService.allowed(role, FormModule.DEVICE, "EDIT")) {
            permissionService.require(FormModule.DEVICE, "CREATE");
        }
        return ApiResponse.ok(deviceService.updatePhoto(id, file));
    }

    @DeleteMapping("/{id:\\d+}/photo")
    public ApiResponse<DeviceView> deletePhoto(@PathVariable Long id) {
        permissionService.require(FormModule.DEVICE, "EDIT");
        return ApiResponse.ok(deviceService.clearPhoto(id));
    }

    @GetMapping("/repairs")
    public ApiResponse<PageResult<DeviceWorkOrderView>> repairs(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        permissionService.require(FormModule.DEVICE_REPAIR, "VIEW");
        return ApiResponse.ok(workOrderService.list(DeviceWorkOrderKind.REPAIR, keyword, page, size));
    }

    @GetMapping("/repairs/customers")
    public ApiResponse<List<DeviceWorkOrderService.RepairCustomer>> repairCustomers() {
        return ApiResponse.ok(workOrderService.customers());
    }

    @GetMapping("/repairs/{id:\\d+}")
    public ApiResponse<DeviceWorkOrderView> repair(@PathVariable Long id) {
        permissionService.require(FormModule.DEVICE_REPAIR, "VIEW");
        return ApiResponse.ok(workOrderService.get(DeviceWorkOrderKind.REPAIR, id));
    }

    @PostMapping("/repairs")
    public ApiResponse<DeviceWorkOrderView> createRepair(@RequestBody UpsertDeviceWorkOrderRequest request) {
        permissionService.require(FormModule.DEVICE_REPAIR, "CREATE");
        return ApiResponse.ok(workOrderService.create(DeviceWorkOrderKind.REPAIR, request, currentUser.entity()));
    }

    @PutMapping("/repairs/{id:\\d+}")
    public ApiResponse<DeviceWorkOrderView> updateRepair(@PathVariable Long id, @RequestBody UpsertDeviceWorkOrderRequest request) {
        permissionService.require(FormModule.DEVICE_REPAIR, "EDIT");
        return ApiResponse.ok(workOrderService.update(DeviceWorkOrderKind.REPAIR, id, request));
    }

    @DeleteMapping("/repairs/{id:\\d+}")
    public ApiResponse<Void> deleteRepair(@PathVariable Long id) {
        permissionService.require(FormModule.DEVICE_REPAIR, "DELETE");
        workOrderService.delete(DeviceWorkOrderKind.REPAIR, id);
        return ApiResponse.ok();
    }

    @PostMapping("/repairs/{id:\\d+}/files")
    public ApiResponse<DeviceWorkOrderView> addRepairFiles(
            @PathVariable Long id,
            @RequestParam("files") MultipartFile[] files
    ) {
        requireOrderWrite(FormModule.DEVICE_REPAIR);
        return ApiResponse.ok(workOrderService.addFiles(DeviceWorkOrderKind.REPAIR, id, Arrays.asList(files)));
    }

    @DeleteMapping("/repairs/{id:\\d+}/files/{fileId:\\d+}")
    public ApiResponse<DeviceWorkOrderView> deleteRepairFile(@PathVariable Long id, @PathVariable Long fileId) {
        requireOrderWrite(FormModule.DEVICE_REPAIR);
        return ApiResponse.ok(workOrderService.removeFile(DeviceWorkOrderKind.REPAIR, id, fileId));
    }

    @GetMapping("/moves")
    public ApiResponse<PageResult<DeviceWorkOrderView>> moves(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        permissionService.require(FormModule.DEVICE_MOVE, "VIEW");
        return ApiResponse.ok(workOrderService.list(DeviceWorkOrderKind.MOVE, keyword, page, size));
    }

    @GetMapping("/moves/{id:\\d+}")
    public ApiResponse<DeviceWorkOrderView> move(@PathVariable Long id) {
        permissionService.require(FormModule.DEVICE_MOVE, "VIEW");
        return ApiResponse.ok(workOrderService.get(DeviceWorkOrderKind.MOVE, id));
    }

    @PostMapping("/moves")
    public ApiResponse<DeviceWorkOrderView> createMove(@RequestBody UpsertDeviceWorkOrderRequest request) {
        permissionService.require(FormModule.DEVICE_MOVE, "CREATE");
        return ApiResponse.ok(workOrderService.create(DeviceWorkOrderKind.MOVE, request, currentUser.entity()));
    }

    @PutMapping("/moves/{id:\\d+}")
    public ApiResponse<DeviceWorkOrderView> updateMove(@PathVariable Long id, @RequestBody UpsertDeviceWorkOrderRequest request) {
        permissionService.require(FormModule.DEVICE_MOVE, "EDIT");
        return ApiResponse.ok(workOrderService.update(DeviceWorkOrderKind.MOVE, id, request));
    }

    @DeleteMapping("/moves/{id:\\d+}")
    public ApiResponse<Void> deleteMove(@PathVariable Long id) {
        permissionService.require(FormModule.DEVICE_MOVE, "DELETE");
        workOrderService.delete(DeviceWorkOrderKind.MOVE, id);
        return ApiResponse.ok();
    }

    @PostMapping("/moves/{id:\\d+}/files")
    public ApiResponse<DeviceWorkOrderView> addMoveFiles(
            @PathVariable Long id,
            @RequestParam("files") MultipartFile[] files
    ) {
        requireOrderWrite(FormModule.DEVICE_MOVE);
        return ApiResponse.ok(workOrderService.addFiles(DeviceWorkOrderKind.MOVE, id, Arrays.asList(files)));
    }

    @DeleteMapping("/moves/{id:\\d+}/files/{fileId:\\d+}")
    public ApiResponse<DeviceWorkOrderView> deleteMoveFile(@PathVariable Long id, @PathVariable Long fileId) {
        requireOrderWrite(FormModule.DEVICE_MOVE);
        return ApiResponse.ok(workOrderService.removeFile(DeviceWorkOrderKind.MOVE, id, fileId));
    }

    @GetMapping("/scraps")
    public ApiResponse<PageResult<DeviceWorkOrderView>> scraps(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        permissionService.require(FormModule.DEVICE_SCRAP, "VIEW");
        return ApiResponse.ok(workOrderService.list(DeviceWorkOrderKind.SCRAP, keyword, page, size));
    }

    @GetMapping("/scraps/{id:\\d+}")
    public ApiResponse<DeviceWorkOrderView> scrap(@PathVariable Long id) {
        permissionService.require(FormModule.DEVICE_SCRAP, "VIEW");
        return ApiResponse.ok(workOrderService.get(DeviceWorkOrderKind.SCRAP, id));
    }

    @PostMapping("/scraps")
    public ApiResponse<DeviceWorkOrderView> createScrap(@RequestBody UpsertDeviceWorkOrderRequest request) {
        permissionService.require(FormModule.DEVICE_SCRAP, "CREATE");
        return ApiResponse.ok(workOrderService.create(DeviceWorkOrderKind.SCRAP, request, currentUser.entity()));
    }

    @PutMapping("/scraps/{id:\\d+}")
    public ApiResponse<DeviceWorkOrderView> updateScrap(@PathVariable Long id, @RequestBody UpsertDeviceWorkOrderRequest request) {
        permissionService.require(FormModule.DEVICE_SCRAP, "EDIT");
        return ApiResponse.ok(workOrderService.update(DeviceWorkOrderKind.SCRAP, id, request));
    }

    @DeleteMapping("/scraps/{id:\\d+}")
    public ApiResponse<Void> deleteScrap(@PathVariable Long id) {
        permissionService.require(FormModule.DEVICE_SCRAP, "DELETE");
        workOrderService.delete(DeviceWorkOrderKind.SCRAP, id);
        return ApiResponse.ok();
    }

    @PostMapping("/scraps/{id:\\d+}/files")
    public ApiResponse<DeviceWorkOrderView> addScrapFiles(
            @PathVariable Long id,
            @RequestParam("files") MultipartFile[] files
    ) {
        requireOrderWrite(FormModule.DEVICE_SCRAP);
        return ApiResponse.ok(workOrderService.addFiles(DeviceWorkOrderKind.SCRAP, id, Arrays.asList(files)));
    }

    @DeleteMapping("/scraps/{id:\\d+}/files/{fileId:\\d+}")
    public ApiResponse<DeviceWorkOrderView> deleteScrapFile(@PathVariable Long id, @PathVariable Long fileId) {
        requireOrderWrite(FormModule.DEVICE_SCRAP);
        return ApiResponse.ok(workOrderService.removeFile(DeviceWorkOrderKind.SCRAP, id, fileId));
    }

    private List<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        for (String part : raw.split(",")) {
            String value = part.trim();
            if (value.isEmpty()) {
                continue;
            }
            ids.add(Long.parseLong(value));
        }
        return ids.isEmpty() ? null : ids;
    }

    private void requireOrderWrite(FormModule module) {
        String role = currentUser.entity().getRole();
        if (!permissionService.allowed(role, module, "EDIT")) {
            permissionService.require(module, "CREATE");
        }
    }
}
