package com.words_hanjoom.domain.scrapNews.service;

import com.words_hanjoom.domain.crawling.entity.Article;
import com.words_hanjoom.domain.users.entity.UserCategory;
import com.words_hanjoom.domain.users.repository.UserCategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScrapNewsService {

    private final UserCategoryRepository userCategoryRepository;

    // 1. 사용자 아이디 가져오기
    public Long getLoginIdFromToken(String token) {
        // 1-1. 토큰에서 사용자 아이디 추출
        Long userId = getLoginIdFromToken(token);
        if (userId == null) {
            throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
        }

        return userId;
    }

    // 2. 사용자 아이디가 선택한 관심 카테고리 추출
    public Article getRandomCategoryNews(Long userId) {
        Long userCategoryId;

        // 2-1. 사용자 아이디로 관심 카테고리 목록 가져오기
        List<UserCategory> userCategories = userCategoryRepository.findUserCategoriesByUserId(userId);
        if (userCategories.isEmpty()) {
            throw new IllegalArgumentException("관심 카테고리가 없습니다.");
        }

        if( userCategories.size() == 1) {
            // 2-2. 관심 카테고리 목록이 1개라면 해당 카테고리 추출
            userCategoryId = userCategories.get(0).getCategory().getCategoryId();
        } else{
            // 2-2. 관심 카테고리 목록이 1개 이상이라면 랜덤 돌려서 하나 선정
            int randomIndex = (int) (Math.random() * userCategories.size());
            userCategoryId = userCategories.get(randomIndex).getCategory().getCategoryId();
        }

        // 2-3. 해당 카테고리의 최신 뉴스 기사 1개 반환


    }



    // 2. 관심 카테고리에 해당하는 뉴스 기사 중 최신 기사 1개 반환
}
