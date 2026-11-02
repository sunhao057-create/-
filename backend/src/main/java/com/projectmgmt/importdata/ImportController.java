package com.projectmgmt.importdata;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.ApiResponse;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class ImportController {

    private final CsvImportService csvImportService;
    private final PermissionService permissionService;
    private final CurrentUser currentUser;

    @GetMapping("/{module}/template")
    public ResponseEntity<byte[]> template(@PathVariable String module) {
        FormModule formModule = parseModule(module);
        permissionService.require(formModule, "IMPORT_EXPORT");
        byte[] body = csvImportService.template(formModule);
        String filename = csvImportService.filename(formModule);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename, StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @PostMapping("/{module}")
    public ApiResponse<ImportResult> importCsv(
            @PathVariable String module,
            @RequestParam("file") MultipartFile file
    ) {
        FormModule formModule = parseModule(module);
        permissionService.require(formModule, "IMPORT_EXPORT");
        return ApiResponse.ok(csvImportService.importFile(formModule, file, currentUser.entity()));
    }

    private FormModule parseModule(String module) {
        try {
            return FormModule.valueOf(module.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("不支持的导入模块");
        }
    }
}
