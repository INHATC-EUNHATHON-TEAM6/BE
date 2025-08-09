package com.hdd.words_hanjoom.domain.users.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class LoginUserRequestDto {
    @NotBlank(message = "아이디는 필수 입력값입니다.")
    private String loginId;
    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    private String password;
}
