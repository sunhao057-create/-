package com.projectmgmt.user;

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
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffController {

    private final UserRepository userRepository;
    private final PermissionService permissionService;

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<StaffView>> list() {
        permissionService.requireAny(
                "VIEW",
                FormModule.PROJECT,
                FormModule.WORKFLOW,
                FormModule.ORG,
                FormModule.OA_ONBOARD,
                FormModule.OA_TRANSFER,
                FormModule.OA_PROMOTION,
                FormModule.OA_RESIGN,
                FormModule.OA_SALARY,
                FormModule.OA_INTERVIEW
        );
        return ApiResponse.ok(
                userRepository.findByStatusOrderByDisplayNameAsc(UserStatus.ENABLED)
                        .stream()
                        .map(StaffView::from)
                        .toList()
        );
    }
}
