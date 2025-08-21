package com.words_hanjoom.domain.users.repository;

import com.words_hanjoom.domain.users.entity.UserCategory;
import com.words_hanjoom.domain.users.entity.UserCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserCategoryRepository extends JpaRepository<UserCategory, UserCategoryId> {
    // 사용자 아이디로 관심 카테고리 목록 가져오기
    List<UserCategory> findUserCategoriesByUserId(Long userId);

    // 사용자 아이디와 카테고리 아이디로 관심 카테고리 조회
//    UserCategory findByUserIdAndCategoryId(Long userId, Long categoryId);
}