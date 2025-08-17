package com.words_hanjoom.infra;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;


@Slf4j
@Component
public class NiklDictionaryClientImpl implements NiklDictionaryClient {

    private final @Qualifier("niklWebClient") WebClient dicWebClient;

    @Value("${nikl.api.key}")
    private String apiKey;

    private final ObjectMapper om = new ObjectMapper();

    public NiklDictionaryClientImpl(WebClient dicWebClient) {
        this.dicWebClient = dicWebClient;
    }

    @Override
    public Optional<String> findLemma(String surface) {
        try {
            String raw = dicWebClient.get()
                    .uri(u -> u.path("/search.do")
                            .queryParam("key", apiKey)
                            .queryParam("q", surface)
                            .queryParam("req_type", "json")
                            .queryParam("start", 1)
                            .queryParam("num", 1)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (raw == null || raw.isBlank()) return Optional.empty();

            JsonNode first = firstNode(om.readTree(raw).path("channel").path("item"));
            // stdict는 word_info 없음 → 바로 word 사용
            String lemma = first.path("word").asText(null);
            return Optional.ofNullable(blankToNull(lemma));
        } catch (Exception e) {
            return Optional.empty();
        }
    }


    public String searchRaw(String word) {
        if (apiKey == null || apiKey.isBlank()) return "NO_API_KEY";
        return dicWebClient.get() // ← webClient -> dicWebClient
                .uri(u -> u.path("/api/search.do")  // baseUrl이 https://stdict.korean.go.kr 인 경우
                        .queryParam("key", apiKey)
                        .queryParam("q", word)
                        .queryParam("req_type", "json")
                        .queryParam("start", 1)
                        .queryParam("num", 10)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block(java.time.Duration.ofSeconds(5));
    }


    @Override
    public Optional<Lexeme> lookup(String lemma) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("[NIKL] api key missing (property=nikl.api.key)");
            return Optional.empty();
        }

        // 1차: exact 시도, 2차: 느슨하게 재시도
        SearchResponse res = fetch(lemma, true);
        if (res == null || res.channel == null || res.channel.item == null || res.channel.item.isEmpty()) {
            res = fetch(lemma, false); // fallback
        }
        if (res == null || res.channel == null || res.channel.item == null || res.channel.item.isEmpty()) {
            log.info("[NIKL] no result for '{}'", lemma);
            return Optional.empty();
        }

        // 정확히 매칭되는 것 우선, 없으면 첫 번째
        SearchItem best = res.channel.item.stream()
                .filter(i -> lemma.equals(i.word))
                .findFirst()
                .orElse(res.channel.item.get(0));

        String definition = best.sense != null ? nvl(best.sense.definition, "") : "";
        String pos  = nvl(best.pos, "");
        String type = (best.sense != null) ? nvl(best.sense.type, "") : "";

        java.util.List<String> categories = new java.util.ArrayList<>();
        if (!pos.isBlank())  categories.add(pos);
        if (!type.isBlank()) categories.add(type);

        byte shoulderNo = 0;
        try { if (best.sup_no != null && !best.sup_no.isBlank()) shoulderNo = (byte) Integer.parseInt(best.sup_no); } catch (Exception ignore) {}

        return Optional.of(new Lexeme(
                nvl(best.word, lemma),
                java.util.List.of(),       // synonyms (검색 API엔 없음)
                java.util.List.of(),       // antonyms (검색 API엔 없음)
                definition,
                categories,
                shoulderNo,
                ""                         // example (검색 API엔 없음)
        ));
    }

    private SearchResponse fetch(String q, boolean strict) {
        try {
            return dicWebClient.get()
                    .uri(u -> {
                        var b = u.path("/api/search.do") // baseUrl: https://stdict.korean.go.kr
                                .queryParam("key", apiKey)
                                .queryParam("q", q)
                                .queryParam("req_type", "json")
                                .queryParam("start", 1)
                                .queryParam("num", 10);
                        if (strict) {
                            b.queryParam("advanced", "y").queryParam("method", "exact");
                        } // 느슨 모드에선 파라미터 생략
                        return b.build();
                    })
                    .retrieve()
                    .bodyToMono(SearchResponse.class)
                    .block(java.time.Duration.ofSeconds(5));
        } catch (Exception e) {
            log.warn("[NIKL] fetch({},{}) failed: {}", q, strict ? "strict" : "loose", e.toString());
            return null;
        }
    }

    private static String nvl(String s, String fb) { return (s == null || s.isBlank()) ? fb : s; }

    /* ====== DTO (응답 매핑) ====== */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    static class SearchResponse { public Channel channel; }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    static class Channel { public java.util.List<SearchItem> item; public Integer total; }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    static class SearchItem {
        public String sup_no;
        public String word;
        public String target_code;
        public Sense sense;
        public String pos;
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    static class Sense {
        public String definition;
        public String type;
        public String link;
    }

    /** 배열이면 0번째, 객체면 그대로, 비정상이면 null */
    private JsonNode firstNodeSafe(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isArray()) return node.size() > 0 ? node.get(0) : null;
        return node; // 객체
    }


    // ---------- helpers ----------
    private static JsonNode firstNode(JsonNode n) {
        return (n != null && n.isArray() && n.size() > 0) ? n.get(0)
                : (n == null ? MissingNode.getInstance() : n);
    }

    private static void forEach(JsonNode node, java.util.function.Consumer<JsonNode> f) {
        if (node == null || node.isMissingNode() || node.isNull()) return;
        if (node.isArray()) for (JsonNode x : node) f.accept(x); else f.accept(node);
    }

    private static boolean isSyn(String type) { return "유의어".equals(type) || "synonym".equalsIgnoreCase(type); }
    private static boolean isAnt(String type) { return "반의어".equals(type) || "antonym".equalsIgnoreCase(type); }
    private static boolean isBlank(String s)   { return s == null || s.isBlank(); }
    private static String nvl(String s)        { return s == null ? "" : s; }

    private void dedup(List<String> list) {
        // LinkedHashSet을 쓰면 순서 유지 + 중복 제거 가능
        Set<String> set = new LinkedHashSet<>(list);

        list.clear();          // 원본 리스트 비우고
        list.addAll(set);      // 중복 제거된 값 다시 추가
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }


}