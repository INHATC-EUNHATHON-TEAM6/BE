package com.words_hanjoom.domain.scrapNews.controller;

import com.words_hanjoom.domain.scrapNews.dto.request.ScrapNewsRequestDto;
import com.words_hanjoom.domain.scrapNews.dto.response.ScrapNewsResponseDto;
import com.words_hanjoom.domain.scrapNews.service.ScrapNewsService;
import com.words_hanjoom.domain.users.dto.request.LoginUserRequestDto;
import com.words_hanjoom.domain.users.dto.response.LoginUserResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/scrap-news")
public class ScrapNewsController {

    private final ScrapNewsService scrapNewsService;


    @Operation(summary = "스크랩 활동을 위한 뉴스 1개 반환", description = "스크랩 활동하기 버튼 클릭 시 사용자가 선택한 관심 카테고리 뉴스기사 중 아직 스크랩 한 기록이 없는 기사 한 개를 반환하기 위한 api 입니다.")
    @GetMapping("/pick-one")
    public ScrapNewsResponseDto scrapNewsOne(@RequestBody ScrapNewsRequestDto dto) {
        // 1. 사용자 아이디 가져오기
        Long userId = scrapNewsService.getLoginId(dto.getUserToken());
        System.out.println("controller userId: " + userId);

        // 2. 사용자 아이디가 선택한 관심 카테고리 추출
        Long categoryId = scrapNewsService.getRandomCategoryNews(userId);
        System.out.println("controller categoryId: " + categoryId);

        // 3. 관심 카테고리에 해당하는 뉴스 기사 중 최신 기사 1개 반환
        return scrapNewsService.getLatestByCategory(userId, categoryId);

    }

}
