package com.words_hanjoom.global.config;
import com.words_hanjoom.global.security.AuthenticationFilter;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationFilter authenticationFilter;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .headers(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/wordbooks/dict/**",
                                "/api/words/**",
                                "/api/scraps/**",
                                "/api/dev/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        authenticationFilter,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<AuthenticationFilter> authenticationFilterRegistration(AuthenticationFilter f) {
        FilterRegistrationBean<AuthenticationFilter> reg = new FilterRegistrationBean<>(f);
        reg.setEnabled(false); // ✅ 서블릿 체인 자동 등록 막고, Security 체인에서만 돌게 함
        return reg;
    }
}