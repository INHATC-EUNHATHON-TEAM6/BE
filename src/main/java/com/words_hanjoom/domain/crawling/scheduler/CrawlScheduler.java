package com.words_hanjoom.domain.crawling.scheduler;

import com.words_hanjoom.domain.crawling.dto.response.CrawlResult;
import com.words_hanjoom.domain.crawling.service.HankyungScraperService;
import com.words_hanjoom.domain.crawling.service.ScienceTimesScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlScheduler {

    private final HankyungScraperService hankyungScraperService;
    private final ScienceTimesScraperService scienceTimesScraperService;

    // 스케줄링할 카테고리 목록만 정의
    private final List<String> SCCategoriesToCrawl = List.of(
            "기초, 응용과학", "신소재, 신기술", "생명과학, 의학", "항공, 우주", "환경, 에너지"
    );

    private final List<String> HKCategoriesToCrawl = List.of(
            "경제", "복지", "금융", "산업", "사회", "문화"
    );

    /** 매일 09:00, 18:00 실행 (Asia/Seoul) */
    @Scheduled(cron = "0 0 9,18 * * *", zone = "Asia/Seoul")
    public void runScheduler() {
        SCCategoriesToCrawl.forEach(category -> {
            try {
                // 서비스에 카테고리 이름만 전달하여 호출
                CrawlResult result = scienceTimesScraperService.scrape(category);
                log.info("[{}] 크롤링 완료: 저장 {}건", category, result.savedCount());
            } catch (Exception e) {
                log.warn("[{}] 크롤링 실패: {}", category, e.getMessage());
            }
        });

        HKCategoriesToCrawl.forEach(category -> {
            try {
                // 서비스에 카테고리 이름만 전달하여 호출
                CrawlResult result = hankyungScraperService.scrape(category);
                log.info("[{}] 크롤링 완료: 저장 {}건", category, result.savedCount());
            } catch (Exception e) {
                log.warn("[{}] 크롤링 실패: {}", category, e.getMessage());
            }
        });
    }
}