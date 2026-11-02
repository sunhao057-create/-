package com.projectmgmt.auth;

import com.projectmgmt.auth.dto.ChangePasswordRequest;
import com.projectmgmt.auth.dto.LoginRequest;
import com.projectmgmt.auth.dto.MiniLoginView;
import com.projectmgmt.auth.dto.MiniTicketView;
import com.projectmgmt.common.ApiResponse;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.security.CurrentUser;
import com.projectmgmt.user.dto.UserView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MiniTokenService miniTokenService;
    private final CurrentUser currentUser;
    private final PermissionService permissionService;
    private final SecurityContextRepository securityContextRepository;

    @PostMapping("/login")
    public ApiResponse<UserView> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        return ApiResponse.ok(authService.login(request.username().trim(), request.password(), httpRequest, httpResponse));
    }

    @PostMapping("/mini-login")
    public ApiResponse<MiniLoginView> miniLogin(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        UserView user = authService.login(request.username().trim(), request.password(), httpRequest, httpResponse);
        return ApiResponse.ok(new MiniLoginView(user, miniTokenService.issueAccess(user.id())));
    }

    @PostMapping("/mini-ticket")
    public ApiResponse<MiniTicketView> miniTicket() {
        String ticket = miniTokenService.issueTicket(currentUser.principal().getId());
        return ApiResponse.ok(new MiniTicketView(ticket, 120));
    }

    @GetMapping("/mini-sso")
    public void miniSso(
            @RequestParam String ticket,
            @RequestParam(required = false) String next,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        try {
            miniTokenService.consumeTicket(ticket, request);
            SecurityContext context = SecurityContextHolder.getContext();
            securityContextRepository.saveContext(context, request, response);
            response.sendRedirect(MiniTokenService.safeNext(next));
        } catch (RuntimeException ex) {
            response.sendRedirect("/m/login");
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me() {
        return ApiResponse.ok(permissionService.toUserView(currentUser.entity()));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(currentUser.principal().getId(), request.oldPassword(), request.newPassword());
        return ApiResponse.ok();
    }
}
