package com.words_hanjoom.domain.scrapNews.service;

import com.words_hanjoom.domain.crawling.entity.Article;
import com.words_hanjoom.domain.crawling.repository.ArticleRepository;
import com.words_hanjoom.domain.scrapNews.dto.response.ScrapNewsResponseDto;
import com.words_hanjoom.domain.users.entity.UserCategory;
import com.words_hanjoom.domain.users.repository.UserCategoryRepository;
import com.words_hanjoom.global.security.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScrapNewsService {

    private final UserCategoryRepository userCategoryRepository;
    private final ArticleRepository articleRepository;
    private final TokenProvider tokenProvider;

    // 1. 사용자 아이디 가져오기
    public Long getLoginId(String token) {
        // 1-1. 토큰에서 사용자 아이디 추출
        Long userId = Long.valueOf(tokenProvider.getLoginIdFromToken(token));
        if (userId == null) {
            throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
        }

        System.out.println("service userId: " + userId);
        return userId;
    }

    // 2. 사용자 아이디가 선택한 관심 카테고리 추출
    public Long getRandomCategoryNews(Long userId) {

        // 2-1. 사용자 아이디로 관심 카테고리 목록 가져오기
        List<Long> userCategories = userCategoryRepository.findActiveCategoryIdsByUserId(userId);
        if (userCategories.isEmpty()) {
            throw new IllegalArgumentException("관심 카테고리가 없습니다.");
        }

        if( userCategories.size() == 1) {
            // 2-2. 관심 카테고리 목록이 1개라면 해당 카테고리 추출
            return userCategories.getFirst();
        } else{
            // 2-2. 관심 카테고리 목록이 1개 이상이라면 랜덤 돌려서 하나 선정
            int randomIndex = (int) (Math.random() * userCategories.size());

            System.out.println("service categoryId: " + userCategories.get(randomIndex));
            return userCategories.get(randomIndex);
        }
    }

    // 2. 관심 카테고리에 해당하는 뉴스 기사 중 최신 기사 1개 반환
    @Transactional(readOnly = true)
    public ScrapNewsResponseDto getLatestByCategory(Long userId, Long categoryId) {
        Article article = articleRepository.pickOneArticleForUserInCategory(userId, categoryId)
                .orElseThrow(() -> new IllegalArgumentException("해당 카테고리에 뉴스 기사가 없습니다."));

        // 최종 선택 기사 ScrapNewsResponseDto로 변환 후 반환
        return ScrapNewsResponseDto.builder()
            .articleId(article.getArticleId())
            .categoryId(article.getCategoryId())
            .title(article.getTitle())
            .content(article.getContent())
            .publishedAt(article.getPublishedAt())
            .reporterName(article.getReporterName())
            .publisher(article.getPublisher())
            .articleUrl(article.getArticleUrl())
            .createdAt(String.valueOf(article.getCreatedAt()))
            .build();
    }
}
