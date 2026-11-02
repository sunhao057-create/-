package com.projectmgmt.device;

import com.projectmgmt.common.ApiResponse;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.security.CurrentUser;
import com.projectmgmt.tech.TechCatalog;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/devices/maint-records")
@RequiredArgsConstructor
public class DeviceMaintController {

    private final DeviceMaintService deviceMaintService;
    private final CurrentUser currentUser;

    @GetMapping("/services")
    public ApiResponse<List<DeviceMaintService.Option>> services() {
        return ApiResponse.ok(deviceMaintService.services());
    }

    @GetMapping("/schema")
    public ApiResponse<TechCatalog.Kind> schema(@RequestParam String service) {
        return ApiResponse.ok(deviceMaintService.schema(service));
    }

    @GetMapping
    public ApiResponse<PageResult<DeviceMaintView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(deviceMaintService.list(keyword, page, size));
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<DeviceMaintView> detail(@PathVariable Long id) {
        return ApiResponse.ok(deviceMaintService.detail(id));
    }

    @PostMapping
    public ApiResponse<DeviceMaintView> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(deviceMaintService.create(body, currentUser.entity()));
    }

    @PutMapping("/{id:\\d+}")
    public ApiResponse<DeviceMaintView> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(deviceMaintService.update(id, body));
    }

    @DeleteMapping("/{id:\\d+}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deviceMaintService.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id:\\d+}/files")
    public ApiResponse<Void> addFiles(
            @PathVariable Long id,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(required = false) String fieldKey) {
        deviceMaintService.addFiles(id, files, fieldKey);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id:\\d+}/files/{fileId}")
    public ApiResponse<Void> removeFile(@PathVariable Long id, @PathVariable Long fileId) {
        deviceMaintService.removeFile(id, fileId);
        return ApiResponse.ok(null);
    }
}
