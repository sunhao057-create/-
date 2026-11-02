package com.projectmgmt.profile;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.user.dto.UserView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final PermissionService permissionService;

    @Transactional
    public UserView updateDisplayName(Long userId, String displayName) {
        SysUser user = userRepository.findById(userId).orElseThrow(() -> ApiException.badRequest("用户不存在"));
        user.setDisplayName(displayName.trim());
        return permissionService.toUserView(user);
    }

    @Transactional
    public UserView updateAvatar(Long userId, MultipartFile file) {
        SysUser user = userRepository.findById(userId).orElseThrow(() -> ApiException.badRequest("用户不存在"));
        user.setAvatarUrl(fileStorageService.saveAvatar(userId, file));
        return permissionService.toUserView(user);
    }
}
