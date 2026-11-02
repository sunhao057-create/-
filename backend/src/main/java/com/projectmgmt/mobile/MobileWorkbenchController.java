package com.projectmgmt.mobile;

import com.projectmgmt.common.ApiResponse;
import com.projectmgmt.mobile.dto.MobileAppGroup;
import com.projectmgmt.mobile.dto.MobileFormSchema;
import com.projectmgmt.role.FormModule;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/mobile")
@RequiredArgsConstructor
public class MobileWorkbenchController {

    private final MobileWorkbenchService mobileWorkbenchService;
    private final MobileFormSchemaService mobileFormSchemaService;
    private final MobileAttachmentService mobileAttachmentService;

    @GetMapping("/workbench")
    public ApiResponse<List<MobileAppGroup>> workbench() {
        return ApiResponse.ok(mobileWorkbenchService.groups());
    }

    @GetMapping("/form-schema")
    public ApiResponse<MobileFormSchema> formSchema(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String path
    ) {
        return ApiResponse.ok(mobileFormSchemaService.schema(module, path));
    }

    @PostMapping("/attachments")
    public ApiResponse<Void> addAttachment(
            @RequestParam String module,
            @RequestParam Long bizId,
            @RequestParam(required = false) String fieldKey,
            @RequestParam("files") MultipartFile[] files
    ) {
        mobileAttachmentService.add(FormModule.valueOf(module.trim()), bizId, fieldKey, files);
        return ApiResponse.ok();
    }

    @DeleteMapping("/attachments")
    public ApiResponse<Void> removeAttachment(
            @RequestParam String module,
            @RequestParam Long bizId,
            @RequestParam Long fileId
    ) {
        mobileAttachmentService.remove(FormModule.valueOf(module.trim()), bizId, fileId);
        return ApiResponse.ok();
    }
}
