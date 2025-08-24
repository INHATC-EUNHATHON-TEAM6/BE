package com.words_hanjoom.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SwaggerConfig {

    private static final String SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .components(new Components()
                        .addSecuritySchemes(SCHEME_NAME, securityScheme()))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME));
    }

    private SecurityScheme securityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP) // HTTP 방식
                .scheme("bearer")               // Bearer 토큰 사용
                .bearerFormat("JWT")            // JWT 형식
                .in(SecurityScheme.In.HEADER)   // 헤더에 포함
                .name("Authorization");         // 헤더 이름
    }

    private Info apiInfo() {
        return new Info()
                .title("CodeArena Swagger")
                .description("CodeArena 유저 및 인증 , ps, 알림에 관한 REST API")
                .version("1.0.0");
    }
}
