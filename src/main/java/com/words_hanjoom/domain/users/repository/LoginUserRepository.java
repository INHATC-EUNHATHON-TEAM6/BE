package com.words_hanjoom.domain.users.repository;

import com.words_hanjoom.domain.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoginUserRepository extends JpaRepository<User, Long> {
     Optional<User> findUserByLoginId(String loginId);
}
