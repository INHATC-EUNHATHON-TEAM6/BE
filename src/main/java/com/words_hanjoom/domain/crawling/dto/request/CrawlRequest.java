package com.words_hanjoom.domain.crawling.dto.request;

import java.util.List;

public record CrawlRequest(
        String category,    // 최종 저장될 분야명
        List<String> sectionUrls    // 해당 카테고리에서 순환할 섹션 URL 목록
) {}
