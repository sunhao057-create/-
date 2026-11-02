package com.projectmgmt.auth;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.security.AuthUser;
import com.projectmgmt.security.DatabaseUserDetailsService;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.user.UserRepository;
import com.projectmgmt.user.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MiniTokenService {

    public static final String HEADER = "Authorization";
    public static final String BEARER = "Bearer ";

    private final MiniTokenRepository miniTokenRepository;
    private final UserRepository userRepository;
    private final DatabaseUserDetailsService userDetailsService;

    @Transactional
    public String issueAccess(Long userId) {
        purgeExpired();
        return persist(userId, SysMiniToken.ACCESS, LocalDateTime.now().plusDays(30), false);
    }

    @Transactional
    public String issueTicket(Long userId) {
        purgeExpired();
        return persist(userId, SysMiniToken.TICKET, LocalDateTime.now().plusMinutes(2), false);
    }

    @Transactional
    public void revokeUser(Long userId) {
        miniTokenRepository.deleteByUserId(userId);
    }

    @Transactional
    public void revoke(String token) {
        if (StringUtils.hasText(token)) {
            miniTokenRepository.deleteById(token.trim());
        }
    }

    @Transactional
    public boolean authenticateBearer(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER)) {
            return false;
        }
        return authenticateToken(header.substring(BEARER.length()).trim(), SysMiniToken.ACCESS, request, false);
    }

    @Transactional
    public void consumeTicket(String ticket, HttpServletRequest request) {
        if (!authenticateToken(ticket, SysMiniToken.TICKET, request, true)) {
            throw ApiException.unauthorized("登录已失效，请重新打开");
        }
    }

    private boolean authenticateToken(String raw, String kind, HttpServletRequest request, boolean consume) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        SysMiniToken token = miniTokenRepository
                .findByIdAndKindAndUsedFalseAndExpiresAtAfter(raw.trim(), kind, LocalDateTime.now())
                .orElse(null);
        if (token == null) {
            return false;
        }
        SysUser user = userRepository.findById(token.getUserId()).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ENABLED) {
            miniTokenRepository.delete(token);
            return false;
        }
        AuthUser principal = (AuthUser) userDetailsService.loadUserById(user.getId());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        if (consume) {
            token.setUsed(true);
            miniTokenRepository.save(token);
        }
        return true;
    }

    private String persist(Long userId, String kind, LocalDateTime expires, boolean used) {
        SysMiniToken token = new SysMiniToken();
        token.setId(UUID.randomUUID().toString().replace("-", ""));
        token.setUserId(userId);
        token.setKind(kind);
        token.setExpiresAt(expires);
        token.setUsed(used);
        miniTokenRepository.save(token);
        return token.getId();
    }

    private void purgeExpired() {
        miniTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now().minusDays(1));
    }

    public static String safeNext(String next) {
        if (next == null || next.isBlank()) {
            return "/m";
        }
        String value = next.trim();
        if (!value.startsWith("/") || value.startsWith("//") || value.contains("://") || value.contains("\\")) {
            return "/m";
        }
        if (value.length() > 300) {
            return "/m";
        }
        return value;
    }
}
