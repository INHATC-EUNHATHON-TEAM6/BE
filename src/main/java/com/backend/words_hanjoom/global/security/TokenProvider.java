package com.backend.words_hanjoom.global.security;

import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
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
public class TokenProvider {

    protected SecretKey key;

    @Value("${jwt.secret}")
    private String secret;

    // key 객체를 JWT 서명용 비밀키로 설정
    @PostConstruct
    protected void init() {
        key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
    }

    // 토큰 생성
    public String createToken(Authentication authentication) {
        long now = System.currentTimeMillis();
        long validity = 1000L * 60 * 60; // 1시간 유효
        String loginId = authentication.getName();

        return Jwts.builder()
                .setSubject(authentication.getName())
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

//
//    public Authentication getAuthentication(Claims claims, String accessToken) {
//        Integer no = claims.get(JwtPayload.NO.getClaim(), Integer.class);
//        if (no != null) {
//            log.info("groupware account login");
//            String id = claims.get(JwtPayload.ID.getClaim(), String.class);
//            CustomAuthenticationToken authentication = CustomAuthenticationToken.builder()
//                    .principal(id)
//                    .credentials(null)
//                    .authorities(Collections.emptyList())
//                    .build();
//
//            authentication.putAdditionalInfo(JwtPayload.ID.getField(), id);
//            authentication.putAdditionalInfo(JwtPayload.NO.getField(), no.toString());
//            authentication.putAdditionalInfo(JwtPayload.ACCESS_TOKEN.getField(), accessToken);
//            authentication.putAdditionalInfo("groupware", "groupware");
//            return authentication;
//        }
//
//        Long id = getLongFromClaims(claims, JwtPayload.ID).longValue();
//
//        CustomAuthenticationToken authentication = CustomAuthenticationToken.builder()
//                .principal(id)
//                .credentials(null)
//                .authorities(Collections.emptyList())
//                .build();
//
//        // Jwt 에서 값 추출
//        String serverName = claims.get(JwtPayload.SERVER_NAME.getClaim(), String.class);
//        Long serverNumber = getLongFromClaims(claims, JwtPayload.SERVER_NUMBER).longValue();
//        Long companyId = getLongFromClaims(claims, JwtPayload.COMPANY_ID).longValue();
//        String userId = getStringFromClaims(claims, JwtPayload.USER_ID);
//
//        // CustomAuthenticationToken 에 추가 정보 저장
//        authentication.putAdditionalInfo(JwtPayload.ID.getField(), id.toString());
//        authentication.putAdditionalInfo(JwtPayload.ACCESS_TOKEN.getField(), accessToken);
//        authentication.putAdditionalInfo(JwtPayload.SERVER_NAME.getField(), serverName);
//        authentication.putAdditionalInfo(JwtPayload.SERVER_NUMBER.getField(), serverNumber.toString());
//        authentication.putAdditionalInfo(JwtPayload.COMPANY_ID.getField(), companyId.toString());
//        authentication.putAdditionalInfo(JwtPayload.USER_ID.getField(), userId);
//
//        // customer_id 가 있으면 추가
//        if (claims.get("customer_id") != null) {
//            Long customerId = getLongFromClaims(claims, JwtPayload.CUSTOMER_ID).longValue();
//            authentication.putAdditionalInfo("customerId", customerId.toString());
//        }
//
//        return authentication;
//    }
//
//    // JWT 토큰 파싱 및 payload(claims) 추출
//    // 시그니처 유효하지 않을경우 null 반환
//    public Claims getClaims(String token) {
//        try {
//            return Jwts.parser()
//                    .verifyWith(key)
//                    .build()
//                    .parseSignedClaims(token)
//                    .getPayload();
//        } catch (Exception e) {
//            return null;
//        }
//    }
//
//    public String createAccessToken(DB_PROP dbProp, TBL_USER_ACCESS userAccess) {
//        String fmsPath = StringUtil.replaceBackslashesWithSlashes(dbProp.getFmsPath());
//        String orderPath = StringUtil.replaceBackslashesWithSlashes(dbProp.getOrderPath());
//
//        return Jwts.builder()
//                .claim("server_number", dbProp.getArsCust())
//                .claim("server_name", dbProp.getCustomerAlias())
//                .claim("id", userAccess.getUserAccessId())
//                .claim("user_id", userAccess.getUserId())
//                .claim("CSM_LEVEL", userAccess.getCsmLevel())
//                .claim("CSM_ETC_LEVEL", userAccess.getCsmEtcLevel())
//                .claim("IS_SUPER_ADMIN", userAccess.getIsSuperAdmin())
//                .claim("IS_ADMIN", userAccess.getIsAdmin())
//                .claim("IS_HIDE", userAccess.getIsHide())
//                .claim("COMPANY_ID", userAccess.getCompanyId())
//                .claim("DAMDANG_ID", userAccess.getDamdangId())
//                .claim("old_pw", userAccess.getUserPwEncrypted() == null || userAccess.getUserPwEncrypted().isEmpty())
//                .claim("ERP_COMPANY_ID", userAccess.getErpCompanyId())
//                .claim("ERP_COMPANY", userAccess.getErpCompany())
//                .claim("FMS_COMPANY", userAccess.getFmsCompany())
//                .claim("RANK", userAccess.getRank())
//                .claim("DEPT", userAccess.getDept())
//                .claim("USER_ACCESS_ID", userAccess.getUserAccessId())
//                .claim("FMS_IP", dbProp.getFmsIp())
//                .claim("FMS_PATH", fmsPath)
//                .claim("FMS_DSN", dbProp.getFmsIp() + ":" + fmsPath)
//                .claim("ORDER_IP", dbProp.getOrderIp())
//                .claim("ORDER_PATH", orderPath)
//                .claim("ORDER_DSN", dbProp.getOrderIp() + ":" + orderPath)
//                .claim("exp", TimeUtil.getNowPlusDays(1).getTime())
//                .expiration(TimeUtil.getNowPlusDays(1))
//                .subject(userAccess.getUserAccessId().toString())
//                .signWith(key)
//                .compact();
//    }
//
//    private Integer getLongFromClaims(Claims claims, JwtPayload payload) {
//        Object value = claims.get(payload.getClaim());
//
//        if (value == null) {
//            value = claims.get(payload.getClaim().toLowerCase());
//        }
//
//        if (value instanceof Integer) {
//            return (Integer)value;
//        }
//
//        return Integer.parseInt((String)value);
//    }
//
//    private String getStringFromClaims(Claims claims, JwtPayload payload) {
//        Object value = claims.get(payload.getClaim());
//
//        if (value instanceof Integer) {
//            return Integer.toString((Integer)value);
//        }
//
//        return (String)value;
//    }
}

