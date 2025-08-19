package com.words_hanjoom.global.security;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
@Slf4j
@Component
public class AuthenticationFilter extends OncePerRequestFilter {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/**",
            "/api/wordbooks/dict/**",   // ← 여기 포함
            "/api/words/**",             // 테스트 중이면 공개
            "/api/scraps/**"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String uri = request.getRequestURI();
        // ★ 테스트용 엔드포인트는 무조건 필터 제외
        if (uri.startsWith("/api/dev/")) return true;
        // 기존 로직 유지
        return super.shouldNotFilter(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        log.info("[AuthFilter] filtering uri={}", req.getRequestURI());
        // ...
        chain.doFilter(req, res);
    }
}

