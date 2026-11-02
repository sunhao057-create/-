package com.projectmgmt.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ImportTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Pm-Import-Token";
    public static final String ATTR = "pm.importToken";

    private final DatabaseUserDetailsService userDetailsService;
    private final String tokenFile;

    public ImportTokenFilter(
            DatabaseUserDetailsService userDetailsService,
            @Value("${app.import-token-file:}") String tokenFile
    ) {
        this.userDetailsService = userDetailsService;
        this.tokenFile = tokenFile;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && StringUtils.hasText(tokenFile)) {
            Path path = Path.of(tokenFile);
            if (Files.isRegularFile(path)) {
                String expected = Files.readString(path, StandardCharsets.UTF_8).trim();
                if (!expected.isBlank() && matched(expected, header.trim())) {
                    UserDetails admin = userDetailsService.loadUserByUsername("admin");
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            admin,
                            null,
                            admin.getAuthorities()
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    request.setAttribute(ATTR, Boolean.TRUE);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private static boolean matched(String expected, String actual) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);
        return left.length == right.length && MessageDigest.isEqual(left, right);
    }
}
