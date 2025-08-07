package com.backend.words_hanjoom.domain.users.repository;

import com.backend.words_hanjoom.domain.users.entity.LoginUserEntity;
import com.hdd.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoginUserRepository extends JpaRepository<User, Long> {
     Optional<LoginUserEntity> findUserByLoginId(String loginId);
}
