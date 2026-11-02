package com.projectmgmt.security;

import com.projectmgmt.auth.MiniTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class MiniTokenFilter extends OncePerRequestFilter {

    private final MiniTokenService miniTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing == null || !existing.isAuthenticated() || !(existing.getPrincipal() instanceof AuthUser)) {
            miniTokenService.authenticateBearer(request);
        }
        filterChain.doFilter(request, response);
    }
}
