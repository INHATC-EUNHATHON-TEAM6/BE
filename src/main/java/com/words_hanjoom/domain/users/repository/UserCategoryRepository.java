package com.words_hanjoom.domain.users.repository;

import com.words_hanjoom.domain.users.entity.UserCategory;
import com.words_hanjoom.domain.users.entity.UserCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCategoryRepository extends JpaRepository<UserCategory, UserCategoryId> {}