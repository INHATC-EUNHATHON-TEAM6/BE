package com.words_hanjoom.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class NiklDictionaryClientImpl implements NiklDictionaryClient {

    private final WebClient dicWebClient;  // baseUrl: https://opendict.korean.go.kr/api (Config에서 주입)

    @Value("${stdict.api.key}")
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

    @Override
    public Optional<Lexeme> lookup(String lemma) {
        try {
            String raw = dicWebClient.get()
                    .uri(u -> u.path("/search.do")
                            .queryParam("key", apiKey)
                            .queryParam("q", lemma)
                            .queryParam("req_type", "json")
                            .queryParam("start", 1)
                            .queryParam("num", 1)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (raw == null || raw.isBlank()) return Optional.empty();

            JsonNode item = firstNode(om.readTree(raw).path("channel").path("item"));

            // === stdict 필드 매핑 ===
            String wordName = nvl(item.path("word").asText(null), lemma);

            // 정의
            String definition = item.path("sense").path("definition").asText("");

            // 범주 비슷한 것: 품사/타입을 카테고리 리스트로 묶어서 반환
            List<String> categories = new ArrayList<>();
            String pos  = item.path("pos").asText("");
            String type = item.path("sense").path("type").asText("");
            if (!pos.isBlank())  categories.add(pos);
            if (!type.isBlank()) categories.add(type);

            // 어깨번호
            byte shoulderNo = 0;
            String sup = item.path("sup_no").asText("");
            if (!sup.isBlank()) {
                try { shoulderNo = (byte) Integer.parseInt(sup); } catch (NumberFormatException ignored) {}
            }

            // 이 응답엔 유의어/반의어/예문이 없음 → 빈 리스트/빈 문자열
            List<String> synonyms = List.of();
            List<String> antonyms = List.of();
            String example = "";

            Lexeme lex = new Lexeme(
                    wordName,
                    synonyms,
                    antonyms,
                    definition,
                    categories,
                    shoulderNo,
                    example
            );
            return Optional.of(lex);

        } catch (Exception e) {
            return Optional.empty();
        }
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

    private static String nvl(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}