package com.words_hanjoom.domain.crawling.scheduler;

import com.words_hanjoom.domain.crawling.dto.request.CrawlRequest;
import com.words_hanjoom.domain.crawling.dto.response.CrawlResult;
import com.words_hanjoom.domain.crawling.service.CrawlService;
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

    private final CrawlService crawlService;

    /** 매시간 정각(Asia/Seoul) 실행 */
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void runHourly() {
        // 1) 카테고리별 섹션 URL 구성
        Map<String, List<String>> sectionMap = Map.of(
                "경제", List.of(
                        "https://www.hankyung.com/economy",
                        "https://www.hankyung.com/economy/economic-policy",
                        "https://www.hankyung.com/economy/macro",
                        "https://www.hankyung.com/economy/forex",
                        "https://www.hankyung.com/economy/tax"
                ),
                "복지", List.of(
                        "https://www.hankyung.com/economy/job-welfare"
                ),
                "금융", List.of("https://www.hankyung.com/financial-market"),
                "산업", List.of("https://www.hankyung.com/industry"),
                "사회", List.of(
                        "https://www.hankyung.com/society",
                        "https://www.hankyung.com/society/administration",
                        "https://www.hankyung.com/society/education",
                        "https://www.hankyung.com/society/employment"
                ),
                "문화", List.of("https://www.hankyung.com/culture")
        );

        // 2) 순회 실행
        sectionMap.forEach((category, urls) -> {
            try {
                CrawlRequest req = new CrawlRequest(category, urls);
                CrawlResult result = crawlService.crawl(req);
                log.info("[{}] 섹션:{}개 → 저장:{}건", category, urls.size(), result.savedCount());
            } catch (Exception e) {
                log.warn("[{}] 크롤 실패: {}", category, e.getMessage());
            }
        });
    }
}
