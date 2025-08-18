package com.words_hanjoom.domain.crawling.controller;

import com.words_hanjoom.domain.crawling.dto.response.CrawlResult;
import com.words_hanjoom.domain.crawling.service.HankyungScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/crawl")
@RequiredArgsConstructor
public class CrawlController {

    private final HankyungScraperService hankyungScraperService;

    @GetMapping("/hankyung/{category}")
    public ResponseEntity<String> scrapeHankyung(@PathVariable String category) {
        try {
            CrawlResult result = hankyungScraperService.scrapeHankyung(category);
            log.info("[{}] 크롤링 완료: 저장 {}건", category, result.savedCount());
            return ResponseEntity.ok("한국경제 [" + category + "] 크롤링 작업이 성공적으로 완료되었습니다. 총 " + result.savedCount() + "건 저장.");
        } catch (IllegalArgumentException e) {
            log.error("잘못된 카테고리 요청: {}", category);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("한국경제 크롤링 중 오류 발생", e);
            return ResponseEntity.status(500).body("한국경제 크롤링 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}