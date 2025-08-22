package com.words_hanjoom.domain.users.controller;

import com.words_hanjoom.domain.users.dto.response.LoginUserResponseDto;
import com.words_hanjoom.domain.users.dto.request.LoginUserRequestDto;
import com.words_hanjoom.domain.users.service.LoginService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;

    @Operation(summary = "로그인 API", description = "사용자가 로그인할 때 사용하는 API입니다.")
    @PostMapping("/login")
    public ResponseEntity<LoginUserResponseDto> login(@RequestBody LoginUserRequestDto dto){
        LoginUserResponseDto response = loginService.login(dto);
        return ResponseEntity.ok(response);
    }
}
