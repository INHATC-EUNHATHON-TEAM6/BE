package com.hdd.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SingupRequestDto {

    @NotBlank(message = "아이디는 필수 입력 값입니다.")
    @Email(message = "유효한 이메일 주소를 입력해주세요.")
    private String loginId;

    @NotBlank(message = "비밀번호는 필수 입력 값입니다.")
    @Size(min = 10, max = 20, message = "비밀번호는 10자 이상 20자 이하로 입력해주세요")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+=-]).{10,20}$",
            message = "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다.")

    private String password;

    @NotBlank(message = "비밀번호 확인은 필수 입력 값입니다.")
    private String passwordCheck;

    @NotBlank(message = "이름은 필수 입력 값입니다.")
    private String name;

    @NotBlank(message = "닉네임은 필수 입력 값입니다.")
    private String nickname;

    @NotBlank(message = "생년월일은 필수 입력 값입니다.")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$",
            message = "생년월일은 YYYY-MM-DD 형식이어야 합니다.")

    private String birthDate;
}
