package com.words_hanjoom.domain.crawling.service;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 카테고리 이름 ↔ ID 매핑
 * 1:"기초, 응용과학", 2:"신소재, 신기술", 3:"생명과학, 의학", 4:"항공, 우주", 5:"환경, 에너지",
 * 6:"경제", 7:"고용복지", 8:"금융", 9:"산업", 10:"사회", 11:"문화"
 */
@Service
public final class CategoryRegistry {
    private CategoryRegistry() {}

    private static final Map<String, Integer> NAME_TO_ID = Map.ofEntries(
            Map.entry("기초, 응용과학", 1),
            Map.entry("신소재, 신기술", 2),
            Map.entry("생명과학, 의학", 3),
            Map.entry("항공, 우주", 4),
            Map.entry("환경, 에너지", 5),
            Map.entry("경제", 6),
            Map.entry("고용복지", 7),      // 복지 == 고용복지
            Map.entry("복지", 7),          // 동의어 매핑
            Map.entry("금융", 8),
            Map.entry("산업", 9),
            Map.entry("사회", 10),
            Map.entry("문화", 11)
    );

    public static int categoryIdOf(String name) {
        Integer id = NAME_TO_ID.get(name);
        if (id == null) throw new IllegalArgumentException("알 수 없는 카테고리: " + name);
        return id;
    }
}
