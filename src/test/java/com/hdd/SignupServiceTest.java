package com.hdd;

import com.hdd.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hdd.dto.SignupRequestDto;
import com.hdd.repository.UserRepository;
import com.hdd.service.SignupService;

import java.time.LocalDate;


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
}
