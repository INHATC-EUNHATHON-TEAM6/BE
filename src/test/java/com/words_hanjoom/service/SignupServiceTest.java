package com.words_hanjoom.service;

import com.words_hanjoom.domain.users.dto.request.SignupRequestDto;
import com.words_hanjoom.domain.users.entity.User;
import com.words_hanjoom.domain.users.repository.UserRepository;
import com.words_hanjoom.domain.users.service.SignupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


import java.time.LocalDate;
import java.util.Optional;


@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SignupService signupService;

    @Test
    void 회원가입_비밀번호불일치_예외발생() {
        // given
        SignupRequestDto dto = new SignupRequestDto();
        dto.setLoginId("test@naver.com");
        dto.setPassword("password123!");
        dto.setPasswordCheck("mismatch123!"); // 다르게 설정

        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            signupService.signup(dto);
        });

        assertEquals("비밀번호와 비밀번호 확인이 일치하지 않습니다.", exception.getMessage());
    }

    @Test
    void 회원가입_정상_저장호출됨() {
        // given
        SignupRequestDto dto = new SignupRequestDto();
        dto.setLoginId("test@naver.com");
        dto.setPassword("password123!");
        dto.setPasswordCheck("password123!");
        dto.setName("권지은");
        dto.setNickname("징징");
        dto.setBirthDate(LocalDate.of(1999, 12, 3));

        // when
        signupService.signup(dto);

        // then
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void 회원가입_중복이메일_예외발생() {
        // given
        SignupRequestDto dto = new SignupRequestDto();
        dto.setLoginId("test@naver.com");
        dto.setPassword("password123!");
        dto.setPasswordCheck("password123!");
        dto.setName("권지은");
        dto.setNickname("징징");
        dto.setBirthDate(LocalDate.of(1999, 12, 3));

        // 이미 존재하는 사용자로 가정
        when(userRepository.findByLoginId(dto.getLoginId()))
                .thenReturn(Optional.of(new User()));

        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            signupService.signup(dto);
        });

        assertEquals("이미 사용 중인 아이디 입니다.", exception.getMessage());
    }
}
