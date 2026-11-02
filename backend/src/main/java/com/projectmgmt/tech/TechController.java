package com.projectmgmt.tech;

import com.projectmgmt.common.ApiResponse;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.security.CurrentUser;
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
@RequestMapping("/api/tech")
@RequiredArgsConstructor
public class TechController {

    private final TechService techService;
    private final CurrentUser currentUser;

    @GetMapping("/{slug}")
    public ApiResponse<PageResult<TechRecordView>> list(
            @PathVariable String slug,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(techService.list(TechKind.fromSlug(slug), keyword, page, size));
    }

    @GetMapping("/{slug}/schema")
    public ApiResponse<TechCatalog.Kind> schema(@PathVariable String slug) {
        return ApiResponse.ok(techService.schema(TechKind.fromSlug(slug)));
    }

    @GetMapping("/{slug}/{id:\\d+}")
    public ApiResponse<TechRecordView> detail(@PathVariable String slug, @PathVariable Long id) {
        return ApiResponse.ok(techService.detail(TechKind.fromSlug(slug), id));
    }

    @PostMapping("/{slug}")
    public ApiResponse<TechRecordView> create(@PathVariable String slug, @RequestBody Map<String, Object> extra) {
        TechKind kind = TechKind.fromSlug(slug);
        TechRecord record = techService.create(kind, extra, currentUser.entity());
        return ApiResponse.ok(techService.toView(record));
    }

    @PutMapping("/{slug}/{id:\\d+}")
    public ApiResponse<TechRecordView> update(
            @PathVariable String slug, @PathVariable Long id, @RequestBody Map<String, Object> extra) {
        return ApiResponse.ok(techService.update(TechKind.fromSlug(slug), id, extra));
    }

    @DeleteMapping("/{slug}/{id:\\d+}")
    public ApiResponse<Void> delete(@PathVariable String slug, @PathVariable Long id) {
        techService.delete(TechKind.fromSlug(slug), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{slug}/{id:\\d+}/files")
    public ApiResponse<Void> addFiles(
            @PathVariable String slug,
            @PathVariable Long id,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(required = false) String fieldKey) {
        techService.addFiles(TechKind.fromSlug(slug), id, files, fieldKey);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{slug}/{id:\\d+}/files/{fileId}")
    public ApiResponse<Void> removeFile(@PathVariable String slug, @PathVariable Long id, @PathVariable Long fileId) {
        techService.removeFile(TechKind.fromSlug(slug), id, fileId);
        return ApiResponse.ok(null);
    }
}
