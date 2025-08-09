package com.hdd.words_hanjoom.domain.users.service;

import com.hdd.words_hanjoom.domain.users.dto.request.LoginUserRequestDto;
import com.hdd.words_hanjoom.domain.users.dto.response.LoginUserResponseDto;
import com.hdd.words_hanjoom.domain.users.entity.LoginUserEntity;
import com.hdd.words_hanjoom.domain.users.repository.LoginUserRepository;
import com.hdd.words_hanjoom.global.security.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final LoginUserRepository loginUserRepository;
    private final TokenProvider tokenProvider;
    private final BCryptPasswordEncoder passwordEncoder;

    // 로그인
    public LoginUserResponseDto login(LoginUserRequestDto loginUserRequestDto) {
        // 입력받은 로그인 아이디로 유저정보 조회
        LoginUserEntity loginUser = loginUserRepository.findUserByLoginId(loginUserRequestDto.getLoginId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 아이디입니다."));

        // 입력받은 비밀번호(암호화)값과 조회된 유저의 비밀번호(암호화)값 비교
        // matches() 메서드는 입력된 비밀번호와 저장된 비밀번호의 해시
        if(!passwordEncoder.matches(loginUserRequestDto.getPassword(), loginUser.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 비밀번호가 일치하면 JWT 토큰 생성
        // UsernamePasswordAuthenticationToken은 인증 정보를 담는 객체로, 로그인 아이디와 권한 정보를 포함
        // Collections.emptyList()는 권한이 없음을 나타내며, 필요시 권한 정보를 추가할 수 있음
        // Authentication 객체를 사용하여 유저 정보를 관리하려고 하기 때문에 해당 객체를 생성해야 함
        Authentication authentication = new UsernamePasswordAuthenticationToken(loginUser.getLoginId(), null, Collections.emptyList());
        String token = tokenProvider.createToken(authentication);
        return new LoginUserResponseDto(token);
    }
}
