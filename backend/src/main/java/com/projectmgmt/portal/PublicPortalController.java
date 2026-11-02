package com.projectmgmt.portal;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.ApiResponse;
import com.projectmgmt.tech.PublicTechView;
import com.projectmgmt.tech.TechCatalog;
import com.projectmgmt.tech.TechLookupsView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicPortalController {

    private final PublicPortalService portal;

    @GetMapping("/config")
    public ApiResponse<PublicConfigView> config() {
        return ApiResponse.ok(portal.config());
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody(required = false) PublicExportRequest request) {
        PublicExportRequest body = request == null ? new PublicExportRequest(null, null, null) : request;
        return portal.export(body.kind(), body.ids(), body.keyword());
    }

    @GetMapping("/lookups")
    public ApiResponse<PublicLookupsView> lookups() {
        return ApiResponse.ok(portal.lookups());
    }

    @GetMapping("/customers")
    public ApiResponse<List<PublicCustomerView>> customers(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(portal.listCustomers(keyword));
    }

    @GetMapping("/customers/{id}")
    public ApiResponse<PublicCustomerView> customer(@PathVariable Long id) {
        return ApiResponse.ok(portal.getCustomer(id));
    }

    @PostMapping("/customers")
    public ApiResponse<PublicCustomerView> submitCustomer(
            @Valid @RequestBody PublicCustomerSubmitRequest request,
            HttpServletRequest http
    ) {
        return ApiResponse.ok(portal.submitCustomer(request, clientIp(http)));
    }

    @GetMapping("/tech/{kind}/lookups")
    public ApiResponse<TechLookupsView> techLookups(@PathVariable String kind) {
        return ApiResponse.ok(portal.techLookups(kind));
    }

    @GetMapping("/tech/{kind}/schema")
    public ApiResponse<TechCatalog.Kind> techSchema(@PathVariable String kind) {
        return ApiResponse.ok(portal.techSchema(kind));
    }

    @GetMapping("/tech/{kind}")
    public ApiResponse<List<PublicTechView>> techList(
            @PathVariable String kind,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(portal.listTech(kind, keyword));
    }

    @GetMapping("/tech/{kind}/{id:\\d+}")
    public ApiResponse<PublicTechView> techDetail(@PathVariable String kind, @PathVariable Long id) {
        return ApiResponse.ok(portal.getTech(kind, id));
    }

    @PostMapping(value = "/tech/{kind}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PublicTechView> submitTech(
            @PathVariable String kind,
            @RequestParam(required = false) String extra,
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            HttpServletRequest http
    ) {
        List<MultipartFile> fileList = files == null ? List.of() : Arrays.asList(files);
        return ApiResponse.ok(portal.submitTech(kind, parseExtra(extra), fileList, clientIp(http)));
    }

    private Map<String, Object> parseExtra(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = tools.jackson.databind.json.JsonMapper.shared()
                    .readValue(raw, Map.class);
            return map == null ? Map.of() : map;
        } catch (Exception ex) {
            throw ApiException.badRequest("表单内容格式不正确");
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
