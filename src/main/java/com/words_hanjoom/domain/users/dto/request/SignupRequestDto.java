package com.words_hanjoom.domain.users.dto.request;
import com.words_hanjoom.domain.users.entity.User;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequestDto {

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

    @NotNull(message = "생년월일은 필수 입력 값입니다.")
    private LocalDate birthDate;

    @NotBlank
    @Size(max = 30)
    private String careerGoal;

    // 선택한 카테고리 ID 목록
    @NotNull(message = "카테고리는 최소 1개 이상 선택해야 합니다.")
    @Size(min = 1, message = "카테고리는 최소 1개 이상 선택해야 합니다.")
    private List<Long> categoryIds;


    public User toEntity() {
        return User.builder()
                .loginId(this.loginId)
                .password(this.password)
                .name(this.name)
                .nickname(this.nickname)
                .birthDate(this.birthDate)
                .careerGoal(this.careerGoal)
                .status("ACTIVE")
                .build();
    }
}
