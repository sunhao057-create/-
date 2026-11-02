package com.projectmgmt.portal;

import com.projectmgmt.common.ApiResponse;
import com.projectmgmt.security.CurrentUser;
import java.util.List;
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
@RequiredArgsConstructor
public class PortalController {

    private final PortalPostService portalPostService;
    private final CurrentUser currentUser;

    @GetMapping("/api/portal/{kind}")
    public ApiResponse<List<PortalPostView>> list(@PathVariable String kind) {
        return ApiResponse.ok(portalPostService.list(kind));
    }

    @GetMapping("/api/portal/{kind}/{id:\\d+}")
    public ApiResponse<PortalPostView> get(@PathVariable String kind, @PathVariable Long id) {
        return ApiResponse.ok(portalPostService.get(kind, id));
    }

    @PostMapping("/api/portal/{kind}")
    public ApiResponse<PortalPostView> create(@PathVariable String kind, @RequestBody PortalPostView request) {
        PortalPostView body = request == null
                ? new PortalPostView(null, kind, null, null, null, null, null, 0, true, null)
                : new PortalPostView(
                        request.id(),
                        kind,
                        request.category(),
                        request.title(),
                        request.summary(),
                        request.body(),
                        request.coverUrl(),
                        request.sortOrder(),
                        request.enabled(),
                        request.createdAt());
        return ApiResponse.ok(portalPostService.create(body, currentUser.entity()));
    }

    @PutMapping("/api/portal/{kind}/{id}")
    public ApiResponse<PortalPostView> update(
            @PathVariable String kind, @PathVariable Long id, @RequestBody PortalPostView request) {
        return ApiResponse.ok(portalPostService.update(id, request));
    }

    @DeleteMapping("/api/portal/{kind}/{id}")
    public ApiResponse<Void> delete(@PathVariable String kind, @PathVariable Long id) {
        portalPostService.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/portal/{kind}/images")
    public ApiResponse<String> upload(@PathVariable String kind, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(portalPostService.upload(file, kind));
    }

    @GetMapping("/api/mini/portal/{kind}")
    public ApiResponse<List<PortalPostView>> published(@PathVariable String kind) {
        return ApiResponse.ok(portalPostService.published(kind));
    }
}
