package com.projectmgmt.region;

import com.projectmgmt.common.ApiResponse;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionRepository regionRepository;
    private final PermissionService permissionService;

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<RegionView>> list() {
        permissionService.require(FormModule.PROJECT, "VIEW");
        return ApiResponse.ok(regionRepository.findAll().stream().map(RegionView::from).toList());
    }
}
