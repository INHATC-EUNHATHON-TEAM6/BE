package com.words_hanjoom.global.util;

import jakarta.servlet.http.HttpServletRequest;

public class HeaderUtil {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    public static String getAccessTokenFromHeader(HttpServletRequest request) {
        String headerValue = request.getHeader(AUTHORIZATION_HEADER);
        if (headerValue != null && headerValue.startsWith(BEARER_PREFIX)) {
            return headerValue.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    // 신규: 순수 헤더 문자열만 받아 처리 (HttpServletRequest 불필요)
    public static String extractBearer(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) return null;
        String h = headerValue.trim();
        if (h.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return h.substring(BEARER_PREFIX.length()).trim();
        }
        return h; // 이미 순수 토큰이면 그대로
    }
}