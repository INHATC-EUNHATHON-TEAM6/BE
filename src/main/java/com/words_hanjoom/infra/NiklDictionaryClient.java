package com.words_hanjoom.infra;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NiklDictionaryClient {

    private final RestClient restClient = RestClient.create();

    @Value("${nikl.base-url:https://opendict.korean.go.kr/api/search}")
    private String baseUrl;

    @Value("${nikl.api-key}")
    private String apiKey;

    /**
     * 표면형 -> 사전 검색 -> 가장 적합한 원형 반환.
     * 우선순위:
     *   1) 완전일치
     *   2) '...다'로 끝나는 표제어 가점
     *   3) 그 외 첫 결과
     */
    public Optional<String> findLemma(String surfaceForm) {
        try {
            String q = URLEncoder.encode(surfaceForm, StandardCharsets.UTF_8);
            String url = String.format(
                    "%s?key=%s&q=%s&req_type=json&part=word&per_page=20",
                    baseUrl, apiKey, q
            );

            NiklSearchResponse resp = restClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(NiklSearchResponse.class);

            if (resp == null || resp.channel == null || resp.channel.items == null || resp.channel.items.isEmpty()) {
                return Optional.empty();
            }

            List<NiklItem> items = resp.channel.items;

            // 1) 완전일치
            Optional<NiklItem> exact = items.stream()
                    .filter(i -> surfaceForm.equals(i.word))
                    .findFirst();
            if (exact.isPresent()) return Optional.of(exact.get().word);

            // 2) '...다' 형태 가점 -> 3) 그 외 첫 결과
            return items.stream()
                    .sorted(Comparator.comparingInt((NiklItem i) -> scoreLemmaLike(i.word)).reversed())
                    .map(i -> i.word)
                    .findFirst();

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private int scoreLemmaLike(String w) {
        // 매우 단순 휴리스틱: '다'로 끝나면 가점
        return (w != null && w.endsWith("다")) ? 2 : 1;
    }

    // ===== 응답 DTO =====
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NiklSearchResponse {
        public Channel channel;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Channel {
        public List<NiklItem> items;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NiklItem {
        @JsonProperty("word")
        public String word;
    }
}