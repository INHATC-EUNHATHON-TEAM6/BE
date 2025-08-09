package com.hdd.words_hanjoom.global.util;

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
}