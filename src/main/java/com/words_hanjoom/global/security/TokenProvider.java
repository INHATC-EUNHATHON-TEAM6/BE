package com.words_hanjoom.global.security;

import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import io.jsonwebtoken.Claims;

import java.util.Collections;
import java.util.Date;

@Log4j2
@Component
@RequiredArgsConstructor

// JWT 토큰 생성, 검증, 파싱, 인증객체로 변환하는 로직
public class
TokenProvider {

    protected SecretKey key;

    @Value("${jwt.secret}")
    private String secret;

    // key 객체를 JWT 서명용 비밀키로 설정
    @PostConstruct
    protected void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret is missing/blank");
        }
        // HS256 권장: 최소 32바이트
        key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

//        key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
    }

    // 토큰 생성
    public String createToken(Authentication authentication) {
        long now = System.currentTimeMillis();
        long validity = 1000L * 60 * 60; // 1시간 유효
        String loginId = authentication.getName();

        return Jwts.builder()
                .setSubject(loginId)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + validity))
                .signWith(key)
                .compact();
    }

    // 토큰 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    // loginId 추출
    public String getLoginIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }

    // JWT 토큰 파싱
    public Claims getClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.error("Failed to parse claims: {}", e.getMessage());
            return null;
        }
    }

    // Claims를 Authentication 객체로 변환
    public Authentication getAuthentication(Claims claims, String accessToken) {
        if (claims == null) return null;

        String loginId = claims.getSubject();

        // 권한은 여기선 비워둠 (필요하면 DB 조회해서 넣을 수 있음)
        return new UsernamePasswordAuthenticationToken(loginId, accessToken, Collections.emptyList());
    }
}