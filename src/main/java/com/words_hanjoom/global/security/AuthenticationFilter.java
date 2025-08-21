package com.words_hanjoom.global.security;
import com.words_hanjoom.global.util.HeaderUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 토큰 인증 필터
 *
 * 헤더에서 토큰을 가져와서 유효한 토큰인지 확인하고,
 * 유효하다면 SecurityContextHolder에 Authentication 객체를 저장하고 다음 필터로 넘어가도록 함
 */
@Slf4j
@Component
@Log4j2
@RequiredArgsConstructor
public class AuthenticationFilter extends OncePerRequestFilter {

    private final TokenProvider tokenProvider;

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/**",
            "/api/wordbooks/dict/**",   // ← 여기 포함
            "/api/words/**",             // 테스트 중이면 공개
            "/api/scraps/**"
    );

    // Swagger, 공개경로는 애초에 필터 제외
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String u = request.getRequestURI();
        return u.startsWith("/v3/api-docs")              // /v3/api-docs, /v3/api-docs/swagger-config 모두 포함
                || u.startsWith("/swagger-ui")               // ✅ /** 제거
                || u.startsWith("/swagger-resources")        // ✅
                || u.startsWith("/webjars")                  // ✅
                || u.equals("/swagger-ui.html")
                || u.equals("/actuator/health")
                || u.equals("/error")
                || u.startsWith("/api/auth")
                || u.equals("/test")
                || u.equals("/favicon.ico");
    }


    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        // 1) AUTHORIZATION 헤더에서 Bearer Token 추출
        String accessToken = HeaderUtil.getAccessTokenFromHeader(request);

        // 2) 토큰이 없거나 빈 문자열이면 익명으로 통과
        if (accessToken == null || accessToken.isBlank()) {
            log.info("No access token found in request header.");
            filterChain.doFilter(request, response);
            return;
        }

        // 3) JWT 토큰 파싱
        Claims claims = tokenProvider.getClaims(accessToken);
        // 4) 토큰이 유효하지 않으면 익명으로 통과
        if (claims != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // claims 이용하여 Authentication 객체를 생성
            Authentication auth = tokenProvider.getAuthentication(claims, accessToken);

            // SecurityContextHolder에 Authentication 객체를 저장
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}