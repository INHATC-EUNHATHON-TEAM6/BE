package com.hdd.service;

import com.hdd.dto.SignupRequestDto;
import com.hdd.entity.User;
import com.hdd.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SignupService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User signup(SignupRequestDto requestDto) {
        // 1. 중복 로그인ID 체크
        if (userRepository.existsByLoginId(requestDto.getLoginId())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디 입니다.");
        }

        // 2. 중복 닉네임 체크
        if (userRepository.existsByNickname(requestDto.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        // 3. 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(requestDto.getPassword());

        // 4. DTO → Entity 변환 및 비밀번호 암호화 반영
        User user = requestDto.toEntity();
        user.setPassword(encodedPassword);

        // 5. DB 저장
        return userRepository.save(user);
    }
}
