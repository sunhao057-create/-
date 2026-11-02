package com.projectmgmt.security;

import com.projectmgmt.user.SysUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private final CurrentUser currentUser;

    public MustChangePasswordFilter(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (Boolean.TRUE.equals(request.getAttribute(ImportTokenFilter.ATTR))) {
            filterChain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser) {
            String path = request.getRequestURI();
            boolean allowed = path.startsWith("/api/auth/")
                    || path.startsWith("/uploads/")
                    || (path.equals("/api/branding") && "GET".equalsIgnoreCase(request.getMethod()))
                    || "OPTIONS".equalsIgnoreCase(request.getMethod());
            if (!allowed) {
                SysUser user = currentUser.entity();
                if (user.isMustChangePassword()) {
                    response.setStatus(403);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"code\":403,\"message\":\"首次登录请先修改密码\",\"data\":null}");
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
