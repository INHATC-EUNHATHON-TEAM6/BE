package com.words_hanjoom.domain.crawling.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SectionRequest {
    private Long categoryId; // 카테고리 id
    private String url; // 기사 URL
}
