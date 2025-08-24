package com.words_hanjoom.global.security;

import com.words_hanjoom.global.util.HeaderUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter extends OncePerRequestFilter {

    private final TokenProvider tokenProvider;

    /** Swagger/공개 경로는 필터 제외 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String u = request.getRequestURI();
        return
                // Swagger & infra
                !(u.startsWith("/v3/api-docs") ||
                        u.startsWith("/swagger-ui") ||
                        u.startsWith("/swagger-resources") ||
                        u.startsWith("/webjars") ||
                        u.equals("/swagger-ui.html") ||
                        u.equals("/actuator/health") ||
                        u.equals("/error") ||
                        u.equals("/favicon.ico") ||

                        // Public APIs
                        u.startsWith("/api/auth") ||
                        u.startsWith("/api/wordbooks/dict") ||
                        u.startsWith("/api/words") ||
                        u.startsWith("/api/scraps") ||
                        u.startsWith("/api/feedback") ||
                        u.startsWith("/api/wordbook") ||
                        u.equals("/test"));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String accessToken = HeaderUtil.getAccessTokenFromHeader(request);

        if (accessToken == null || accessToken.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Claims claims = tokenProvider.getClaims(accessToken);

        if (claims != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Authentication auth = tokenProvider.getAuthentication(claims, accessToken);
            if (auth != null) {
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }
}
