package com.hdd.repository;

import com.hdd.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // 로그인 ID(loginId)로 사용자 정보를 조회하는 메서드
    // 결과가 없을 수도 있으니 Optional<User>로 감싸서 반환
    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId); // 특정 로그인 ID가 이미 데이터베이스에 존재하는지 여부를 확인하는 메서드
    boolean existsByNickname(String nickname); // 닉네임 중복 여부 확인용 메서드
}