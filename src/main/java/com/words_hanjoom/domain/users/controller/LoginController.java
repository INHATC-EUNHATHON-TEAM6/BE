package com.hdd.words_hanjoom.domain.users.controller;

import com.hdd.words_hanjoom.domain.users.dto.request.LoginUserRequestDto;
import com.hdd.words_hanjoom.domain.users.dto.response.LoginUserResponseDto;
import com.hdd.words_hanjoom.domain.users.service.LoginService;
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

    @PostMapping("/login")
    public ResponseEntity<LoginUserResponseDto> login(@RequestBody LoginUserRequestDto dto){
        LoginUserResponseDto response = loginService.login(dto);
        return ResponseEntity.ok(response);
    }
}
