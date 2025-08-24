package com.words_hanjoom.domain.users.controller;

import com.words_hanjoom.domain.users.dto.request.SignupRequestDto;
import com.words_hanjoom.domain.users.service.SignupService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SignupService signupService;

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody @Valid SignupRequestDto signupRequestDto) {
        signupService.signup(signupRequestDto);
        return ResponseEntity.ok("회원가입이 완료되었습니다.");
    }
}