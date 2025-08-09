package com.words_hanjoom.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.words_hanjoom.domain.users.dto.SignupRequestDto;
import com.words_hanjoom.domain.users.entity.User;
import com.words_hanjoom.domain.users.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SignupIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("회원가입 성공 테스트")
    void 회원가입_성공() throws Exception {
        // given
        SignupRequestDto dto = new SignupRequestDto();
        dto.setLoginId("integration@naver.com");
        dto.setPassword("testPassword123!");
        dto.setPasswordCheck("testPassword123!");
        dto.setName("권지은");
        dto.setNickname("징징통합");
        dto.setBirthDate(LocalDate.of(1999, 12, 3));

        String requestBody = objectMapper.writeValueAsString(dto);

        // when & then
        mockMvc.perform(post("/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());
        // 필요하면 .andExpect(jsonPath("message").value("회원가입 완료")) 등도 가능
    }

    @Test
    @DisplayName("회원가입 후 DB 저장 여부 확인")
    void 회원가입_성공_후_DB저장_확인() throws Exception {
        // given
        String email = "savecheck@naver.com";

        SignupRequestDto dto = new SignupRequestDto();
        dto.setLoginId(email);
        dto.setPassword("pass1234!");
        dto.setPasswordCheck("pass1234!");
        dto.setName("권지은");
        dto.setNickname("DB확인");
        dto.setBirthDate(LocalDate.of(1999, 12, 3));

        String requestBody = objectMapper.writeValueAsString(dto);

        // when
        mockMvc.perform(post("/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        // then: 직접 DB에서 조회
        Optional<User> savedUser = userRepository.findByLoginId(email);
        assertThat(savedUser).isPresent();  // 저장되었는지 확인
        assertThat(savedUser.get().getNickname()).isEqualTo("DB확인");
    }

    @Test
    @DisplayName("비밀번호 불일치로 인한 회원가입 실패")
    void 회원가입_비밀번호불일치() throws Exception {
        SignupRequestDto dto = new SignupRequestDto();
        dto.setLoginId("failcase@naver.com");
        dto.setPassword("12345Aa!");
        dto.setPasswordCheck("12345Bb!");  // 다른 비번
        dto.setName("권지은");
        dto.setNickname("실패");
        dto.setBirthDate(LocalDate.of(1999, 12, 3));

        String requestBody = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest()); // 예외 처리 시 상태코드가 400으로 나와야 함
    }
}
