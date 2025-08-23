package com.words_hanjoom.domain.users.repository;

import com.words_hanjoom.domain.users.entity.UserCategory;
import com.words_hanjoom.domain.users.entity.UserCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserCategoryRepository extends JpaRepository<UserCategory, UserCategoryId> {
    // 사용자 아이디로 관심 카테고리 목록 가져오기
    @Query("""
        select uc.id.categoryId
        from UserCategory uc
        where uc.id.userId = :userId
          and uc.deletedAt is null
        """)
    List<Long> findActiveCategoryIdsByUserId(@Param("userId") Long userId);

}