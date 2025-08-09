package com.hdd.words_hanjoom.domain.users.repository;

import com.hdd.words_hanjoom.domain.users.entity.LoginUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoginUserRepository extends JpaRepository<LoginUserEntity, Long> {
     Optional<LoginUserEntity> findUserByLoginId(String loginId);
}
