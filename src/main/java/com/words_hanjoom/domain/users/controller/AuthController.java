package com.hdd.controller;

import com.hdd.dto.SignupRequestDto;
import com.hdd.service.SignupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SignupService signupService;

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody @Valid SignupRequestDto signupRequestDto) {
        signupService.signup(signupRequestDto);
        return ResponseEntity.ok("회원가입이 완료되었습니다.");
    }
}