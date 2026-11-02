package com.projectmgmt.profile;

import com.projectmgmt.common.ApiResponse;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.profile.dto.UpdateProfileRequest;
import com.projectmgmt.security.CurrentUser;
import com.projectmgmt.user.UserService;
import com.projectmgmt.user.dto.LoginLogView;
import com.projectmgmt.user.dto.UserView;
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
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final UserService userService;
    private final CurrentUser currentUser;

    @PutMapping
    public ApiResponse<UserView> update(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(profileService.updateDisplayName(currentUser.principal().getId(), request.displayName()));
    }

    @PostMapping("/avatar")
    public ApiResponse<UserView> avatar(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(profileService.updateAvatar(currentUser.principal().getId(), file));
    }

    @GetMapping("/login-logs")
    public ApiResponse<PageResult<LoginLogView>> loginLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(userService.myLoginLogs(currentUser.principal().getId(), page, size));
    }
}
