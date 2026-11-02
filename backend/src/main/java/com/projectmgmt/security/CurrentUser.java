package com.projectmgmt.security;

import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final UserRepository userRepository;

    public AuthUser principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser authUser)) {
            throw new IllegalStateException("未登录");
        }
        return authUser;
    }

    public SysUser entity() {
        return userRepository.findById(principal().getId())
                .orElseThrow(() -> new IllegalStateException("用户不存在"));
    }
}
