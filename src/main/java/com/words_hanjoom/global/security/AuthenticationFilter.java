package com.words_hanjoom.global.security;

import com.words_hanjoom.global.util.HeaderUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.util.List;

/**
 * 토큰 인증 필터
 * - 화이트리스트 및 OPTIONS는 바로 통과
 * - 토큰 없으면 검증 시도하지 않음(불필요 경고 방지)
 * - 유효한 토큰이면 SecurityContext에 Authentication 채움
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class AuthenticationFilter extends OncePerRequestFilter {

    private final TokenProvider tokenProvider;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> WHITELIST = List.of(
            "/api/auth/**",
            "/api/wordbook/**",
            "/api/dev/nikl/**"
    );

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 화이트리스트/OPTIONS는 shouldNotFilter에서 걸러지지만
        // 방어적으로도 토큰 없으면 바로 패스
        String accessToken = HeaderUtil.getAccessTokenFromHeader(request);
        if (accessToken == null || accessToken.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = tokenProvider.getClaims(accessToken);
            if (claims != null) {
                Authentication authentication = tokenProvider.getAuthentication(claims, accessToken);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            // 토큰 불량 등 예외 시 컨텍스트 비움(실패는 이후 인가 단계에서 처리)
            SecurityContextHolder.clearContext();
            log.debug("JWT processing failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * true를 반환하면 이 필터를 실행하지 않음.
     * - OPTIONS (CORS preflight)
     * - 화이트리스트 패턴
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        // Preflight는 전부 통과
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 화이트리스트 패턴 매칭 시 통과
        for (String pattern : WHITELIST) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }

        return false;
    }
}
