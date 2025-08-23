package com.words_hanjoom.domain.scrapNews.controller;

import com.words_hanjoom.domain.scrapNews.dto.request.ScrapNewsRequestDto;
import com.words_hanjoom.domain.scrapNews.dto.response.ScrapNewsResponseDto;
import com.words_hanjoom.domain.scrapNews.service.ScrapNewsService;
import com.words_hanjoom.global.util.HeaderUtil;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/scrap-news")
public class ScrapNewsController {

    private final ScrapNewsService scrapNewsService;


    @Operation(summary = "스크랩 활동을 위한 뉴스 1개 반환", description = "스크랩 활동하기 버튼 클릭 시 사용자가 선택한 관심 카테고리 뉴스기사 중 아직 스크랩 한 기록이 없는 기사 한 개를 반환하기 위한 api 입니다.")
    @PostMapping("/pick-one")
    public ScrapNewsResponseDto scrapNewsOne(@RequestBody ScrapNewsRequestDto newsDto) {

    if (newsDto == null || newsDto.getUserToken() == null || newsDto.getUserToken().isBlank()) {
            throw new IllegalArgumentException("userToken 누락");
        }
        String token = stripBearer(newsDto.getUserToken());   // "Bearer "로 들어와도 방어
        System.out.println("controller token: " + token);

        // 1. 사용자 아이디 가져오기
        Long userId = scrapNewsService.getUserId(token);
        System.out.println("controller userId: " + userId);

        // 2. 사용자 아이디가 선택한 관심 카테고리 추출
        Long categoryId = scrapNewsService.getRandomCategoryNews(userId);
        System.out.println("controller categoryId: " + categoryId);

        // 3. 관심 카테고리에 해당하는 뉴스 기사 중 최신 기사 1개 반환
        return scrapNewsService.getLatestByCategory(userId, categoryId);

    }

    private String stripBearer(String newsDto) {
        String v = newsDto.trim();
        if (v.regionMatches(true, 0, "Bearer ", 0, 7)) {
            v = v.substring(7).trim();
        }
        return v;
    }

}
