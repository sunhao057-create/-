package com.projectmgmt.branding;

import com.projectmgmt.common.ApiResponse;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/branding")
@RequiredArgsConstructor
public class BrandingController {

    private final BrandingService brandingService;
    private final PermissionService permissionService;

    @GetMapping
    public ApiResponse<BrandingView> get() {
        return ApiResponse.ok(brandingService.get());
    }

    @PutMapping
    public ApiResponse<BrandingView> update(@Valid @RequestBody UpdateBrandingRequest request) {
        permissionService.require(FormModule.BRANDING, "EDIT");
        return ApiResponse.ok(brandingService.updateName(request.name()));
    }

    @PostMapping("/logo")
    public ApiResponse<BrandingView> uploadLogo(@RequestParam("file") MultipartFile file) {
        permissionService.require(FormModule.BRANDING, "EDIT");
        return ApiResponse.ok(brandingService.updateLogo(file));
    }

    @PutMapping("/theme")
    public ApiResponse<BrandingView> updateTheme(@Valid @RequestBody UpdateThemeRequest request) {
        permissionService.require(FormModule.THEME, "EDIT");
        return ApiResponse.ok(brandingService.updateTheme(request.themeId()));
    }
}
