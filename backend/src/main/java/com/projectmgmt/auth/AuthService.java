package com.projectmgmt.auth;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.security.AuthUser;
import com.projectmgmt.user.LoginLogRepository;
import com.projectmgmt.user.SysLoginLog;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.user.UserStatus;
import com.projectmgmt.user.dto.UserView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UserRepository userRepository;
    private final LoginLogRepository loginLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;
    private final MiniTokenService miniTokenService;

    @Value("${server.servlet.session.cookie.name:PMSESSION}")
    private String sessionCookieName;

    @Transactional
    public UserView login(String username, String password, HttpServletRequest request, HttpServletResponse response) {
        String ip = clientIp(request);
        String ua = userAgent(request);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, password)
            );
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);

            AuthUser principal = (AuthUser) authentication.getPrincipal();
            SysUser user = userRepository.findById(principal.getId()).orElseThrow();
            saveLog(user.getId(), user.getUsername(), true, null, ip, ua);
            return permissionService.toUserView(user);
        } catch (DisabledException ex) {
            saveLog(null, username, false, "账号已停用", ip, ua);
            throw ApiException.unauthorized("账号已停用");
        } catch (BadCredentialsException ex) {
            saveLog(null, username, false, "用户名或密码错误", ip, ua);
            throw ApiException.unauthorized("用户名或密码错误");
        }
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AuthUser authUser) {
                miniTokenService.revokeUser(authUser.getId());
            }
        } catch (RuntimeException ignored) {
            /* still clear session */
        }
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        securityContextRepository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
        expireSessionCookie(response);
    }

    private void expireSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(sessionCookieName, "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        validatePassword(newPassword);
        SysUser user = userRepository.findById(userId).orElseThrow(() -> ApiException.badRequest("用户不存在"));
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("原密码不正确");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("新密码不能与原密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordPlain(newPassword);
        user.setMustChangePassword(false);
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw ApiException.badRequest("密码至少 8 位");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw ApiException.badRequest("密码需同时包含字母和数字");
        }
    }

    public static void validateUsername(String username) {
        if (username == null || !username.matches("^[a-zA-Z0-9_]{4,32}$")) {
            throw ApiException.badRequest("登录账号为 4-32 位字母、数字或下划线");
        }
    }

    private void saveLog(Long userId, String username, boolean success, String reason, String ip, String ua) {
        SysLoginLog log = new SysLoginLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setSuccess(success);
        log.setFailReason(reason);
        log.setIp(ip);
        log.setUserAgent(ua);
        loginLogRepository.save(log);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) {
            return "";
        }
        return ua.length() > 250 ? ua.substring(0, 250) : ua;
    }
}
