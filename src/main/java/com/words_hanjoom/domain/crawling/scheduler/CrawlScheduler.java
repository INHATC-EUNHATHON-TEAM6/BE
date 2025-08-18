package com.words_hanjoom.domain.crawling.scheduler;

import com.words_hanjoom.domain.crawling.dto.response.CrawlResult;
import com.words_hanjoom.domain.crawling.service.HankyungScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlScheduler {

    private final HankyungScraperService hankyungScraperService;

    // 스케줄링할 카테고리 목록만 정의
    private final List<String> categoriesToCrawl = List.of(
            "경제", "복지", "금융", "산업", "사회", "문화"
    );

    /** 매시간 정각(Asia/Seoul) 실행 */
    //@Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul") // 필요에 따라 주석 해제
    public void runHourly() {
        categoriesToCrawl.forEach(category -> {
            try {
                // 서비스에 카테고리 이름만 전달하여 호출
                CrawlResult result = hankyungScraperService.scrapeHankyung(category);
                log.info("[{}] 크롤링 완료: 저장 {}건", category, result.savedCount());
            } catch (Exception e) {
                log.warn("[{}] 크롤링 실패: {}", category, e.getMessage());
            }
        });
    }
}