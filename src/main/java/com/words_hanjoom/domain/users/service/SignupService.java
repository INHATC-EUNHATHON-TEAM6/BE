package com.words_hanjoom.domain.users.service;

import com.words_hanjoom.domain.users.dto.request.SignupRequestDto;
import com.words_hanjoom.domain.users.entity.User;
import com.words_hanjoom.domain.users.repository.UserRepository;
import com.words_hanjoom.domain.users.entity.Category;
import com.words_hanjoom.domain.users.repository.CategoryRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SignupService {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;

    public User signup(SignupRequestDto dto) {
        // 1) 비밀번호 일치 확인
        if (!dto.getPassword().equals(dto.getPasswordCheck())) {
            throw new IllegalArgumentException("비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }

        // 2) 중복 체크
        if (userRepository.existsByLoginId(dto.getLoginId())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디 입니다.");
        }
        if (userRepository.existsByNickname(dto.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        // 3) 비밀번호 인코딩
        String encodedPassword = passwordEncoder.encode(dto.getPassword());

        // 4) DTO -> Entity
        User user = dto.toEntity();           // careerGoal 포함됨
        user.setPassword(encodedPassword);

        // 5) 유저 저장 (PK 확보)
        User saved = userRepository.save(user);

        // 6) 선택한 카테고리 검증 및 매핑
        if (dto.getCategoryIds() != null && !dto.getCategoryIds().isEmpty()) {
            List<Category> categories = categoryRepository.findByCategoryIdIn(dto.getCategoryIds());

            // 요청한 ID 수와 실제 조회된 수가 다르면 잘못된 ID 포함
            if (categories.size() != new HashSet<>(dto.getCategoryIds()).size()) {
                throw new IllegalArgumentException("유효하지 않은 분야가 포함되어 있습니다.");
            }

            // 사용자-카테고리 연관 추가 (User.addCategory 내부에서 UserCategory 생성)
            categories.forEach(saved::addCategory);
        }

        // 7) 최종 엔티티 반환 (연관은 cascade로 함께 반영)
        return saved;
    }
}