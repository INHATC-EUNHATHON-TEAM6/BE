// src/main/java/com/words_hanjoom/infra/dictionary/AiDictionaryClientImpl.java
package com.words_hanjoom.infra.dictionary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.words_hanjoom.domain.wordbooks.dto.ai.SenseCandidate;
import com.words_hanjoom.domain.wordbooks.dto.ai.SenseChoiceResult;
import com.words_hanjoom.domain.wordbooks.dto.response.DictEntry;
import com.words_hanjoom.domain.wordbooks.entity.Word;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiDictionaryClientImpl implements AiDictionaryClient {

    @Qualifier("openAiWebClient")
    private final WebClient openAiWebClient;

    @Value("${openai.model}")
    private String openAiModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<SenseChoiceResult> chooseSenseByAI(String context, List<DictEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Mono.just(new SenseChoiceResult(-1, null, null, "no candidates"));
        }
        var cands = new ArrayList<SenseCandidate>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.get(i);
            cands.add(new SenseCandidate(
                    i, e.getLemma(), e.getTargetCode(), e.getSenseNo(),
                    e.getDefinition(), e.getExample(), e.getCategories()
            ));
        }

        return callLLM(context, cands)
                .switchIfEmpty(Mono.fromSupplier(() -> heuristicPick(context, cands)));
    }

    private Mono<SenseChoiceResult> callLLM(String context, List<SenseCandidate> cands) {
        // 프롬프트
        var system = """
        너는 한국어 사전 뜻 분별기다. 주어진 문맥(context)에 가장 맞는 후보 하나를 고른다.
        반드시 JSON 객체만 출력해라: {"idx": number, "targetCode": number, "senseNo": number|null, "reason": "string"}
        """;
        var user = Map.of(
                "context", context == null ? "" : context,
                "candidates", cands
        );

        var payload = Map.of(
                "model", openAiModel,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user",   "content", toJson(user))
                )
        );

        return openAiWebClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(body -> {
                    try {
                        var root = objectMapper.readTree(body);
                        var content = root.path("choices").get(0).path("message").path("content").asText();
                        if (content == null || content.isBlank()) return Mono.empty();

                        var node = objectMapper.readTree(content);
                        int idx = node.path("idx").asInt(-1);
                        Long tc = node.hasNonNull("targetCode") ? node.get("targetCode").asLong() : null;
                        Integer sn = node.hasNonNull("senseNo") ? node.get("senseNo").asInt() : null;
                        String reason = node.hasNonNull("reason") ? node.get("reason").asText() : "ai";

                        return Mono.just(new SenseChoiceResult(idx, tc, sn, reason));
                    } catch (Exception e) {
                        // 파싱 실패 → 빈 값 반환해서 휴리스틱으로 폴백
                        return Mono.empty();
                    }
                });
    }

    private String toJson(Object o) {
        try { return objectMapper.writeValueAsString(o); }
        catch (Exception e) { return "{}"; }
    }
    // ===== 공통 헬퍼 =====

    private SenseChoiceResult heuristicPick(String context, List<SenseCandidate> cands) {
        String ctx = Optional.ofNullable(context).orElse("").toLowerCase(Locale.ROOT);

        int bestIdx = 0, bestScore = Integer.MIN_VALUE;
        for (var c : cands) {
            int s = 0;
            if (c.definition() != null) s += overlap(ctx, c.definition().toLowerCase(Locale.ROOT));
            if (c.example() != null)    s += overlap(ctx, c.example().toLowerCase(Locale.ROOT));
            if (c.categories() != null && !c.categories().isBlank()) {
                var firstCat = c.categories().split(",")[0].trim().toLowerCase(Locale.ROOT);
                if (!firstCat.isBlank() && ctx.contains(firstCat)) s += 2;
            }
            if (s > bestScore) { bestScore = s; bestIdx = c.idx(); }
        }
        var chosen = cands.get(bestIdx);
        return new SenseChoiceResult(chosen.idx(), chosen.targetCode(), chosen.senseNo(), "fallback-heuristic");
    }

    private int overlap(String a, String b) {
        if (a.isEmpty() || b == null || b.isEmpty()) return 0;
        int score = 0;
        for (String tok : a.split("[^가-힣a-zA-Z0-9]+")) {
            if (tok.length() < 2) continue;
            if (b.contains(tok)) score++;
        }
        return score;
    }

    private Mono<String> callChatJson(String system, String user) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", openAiModel);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
        ));

        return openAiWebClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)                 // Mono<String>
                .timeout(Duration.ofSeconds(15))
                .doOnNext(j -> log.info("[AI] Raw: {}", j))
                // ↓ content를 String으로 **보장** (JsonPath가 Object를 줄 수도 있음)
                .flatMap(j -> {
                    try {
                        Object contentObj = com.jayway.jsonpath.JsonPath
                                .read(j, "$.choices[0].message.content");
                        if (contentObj == null) return Mono.empty();
                        if (contentObj instanceof String s) return Mono.just(s);
                        // content가 객체로 오면 JSON 문자열로 직렬화
                        return Mono.just(new ObjectMapper().writeValueAsString(contentObj));
                    } catch (Exception ex) {
                        log.warn("[AI] JsonPath parse error: {}", ex.toString());
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.warn("[AI] HTTP/parse error: {}", e.toString());
                    return Mono.empty();                  // ← Mono<String> 유지
                });
    }



    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static List<String> strList(JsonNode arr) {
        if (arr == null || !arr.isArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonNode e : arr) if (e != null && e.isTextual()) out.add(e.asText());
        return out;
    }

    private static String toJson(ObjectMapper om, Object o) {
        try { return om.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }

    // ===== 구현 =====

    @Override
    public Mono<DictEntry> defineFromAi(String surface, @Nullable String context) {
        String user = """
        당신은 한국어 사전 편찬자입니다.
        표제어: %s
        맥락(선택): %s

        - 정의와 예문, 유의어/반의어/분야(categories)는 모두 **한국어**로 작성하세요.
        - JSON 객체만 반환하세요:
        {
          "lemma": "...",
          "definition": "...",
          "example": "...",
          "synonyms": ["..."],
          "antonyms": ["..."],
          "categories": ["..."]
        }
        """.formatted(surface, context == null ? "" : context);

        String system = "You are a helpful assistant that returns ONLY a valid JSON object.";

        return callChatJson(system, user)
                .flatMap(jsonContent -> {
                    if (jsonContent == null || jsonContent.isBlank()) return Mono.empty();
                    try {
                        JsonNode node = objectMapper.readTree(jsonContent);
                        String lemma      = nz(node.path("lemma").asText(null));
                        String definition = nz(node.path("definition").asText(null));
                        String example    = nz(node.path("example").asText(""));
                        List<String> synonyms = strList(node.get("synonyms"));
                        List<String> antonyms = strList(node.get("antonyms"));
                        List<String> cats     = strList(node.get("categories"));
                        if (lemma == null || definition == null || lemma.isBlank() || definition.isBlank()) {
                            return Mono.empty();
                        }
                        DictEntry entry = DictEntry.builder()
                                .lemma(lemma)
                                .definition(definition)
                                .example(example)
                                .synonyms(synonyms)
                                .antonyms(antonyms)
                                .categories(String.join(",", cats))
                                .targetCode(0L)
                                .senseNo(null)
                                .shoulderNo((byte)0)
                                .build();
                        return Mono.just(entry);
                    } catch (Exception e) {
                        log.warn("[AI] defineFromAi parse error: {}", e.toString());
                        return Mono.empty();
                    }
                });
    }

    @Override
    public Mono<DictEntry> pickBestFromNiklCandidates(String surface, String context, List<DictEntry> candidates) {
        if (candidates == null || candidates.isEmpty()) return Mono.empty();

        // 후보를 최대 8개로 제한(토큰/지연 관리)
        int limit = Math.min(8, candidates.size());
        List<Map<String, Object>> cands = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            DictEntry e = candidates.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", i);
            m.put("lemma", e.getLemma());
            m.put("definition", nz(e.getDefinition()));
            m.put("example", nz(e.getExample()));
            m.put("categories", nz(e.getCategories()));
            m.put("targetCode", e.getTargetCode());
            m.put("senseNo", e.getSenseNo());
            cands.add(m);
        }

        String user = """
        다음은 표제어의 후보 의미 목록입니다. 한국어 기사 **맥락**과 가장 잘 부합하는 **단 하나의** 후보 index를 고르세요.
        - 표제어: %s
        - 맥락(앞부분만): %s
        - 후보들(JSON): %s

        반드시 다음 **JSON 객체만** 반환하세요:
        {"choice": <정수 인덱스>, "confidence": <0..1>}
        """.formatted(surface, clip(context, 1400), toJson(objectMapper, cands));

        String system = "You are a strict disambiguation engine. Respond ONLY with a JSON object.";

        return callChatJson(system, user)
                .flatMap(json -> {
                    try {
                        JsonNode n = objectMapper.readTree(json);
                        int idx = n.path("choice").asInt(-1);
                        return (idx >= 0 && idx < limit) ? Mono.just(candidates.get(idx)) : Mono.empty();
                    } catch (Exception e) {
                        log.warn("[AI] pickBestFromNiklCandidates parse error: {}", e.toString());
                        return Mono.empty();
                    }
                });
    }

    @Override
    public Mono<Boolean> isDbCandidateFit(String surface, String context, Word c) {
        String system = "You are a strict disambiguation engine. Respond ONLY with a JSON object.";
        Map<String,Object> cand = Map.of(
                "wordName", nz(c.getWordName()),
                "definition", nz(c.getDefinition()),
                "example", nz(c.getExample()),
                "categories", nz(c.getWordCategory()),
                "targetCode", c.getTargetCode(),
                "senseNo", c.getSenseNo()
        );
        String user = """
    한국어 기사 **맥락**과 아래 후보 뜻이 잘 맞는지 판단하세요.
    - 표제어: %s
    - 맥락(앞부분): %s
    - 후보(JSON): %s

    다음 **JSON만** 반환하세요:
    {"fit": true | false, "confidence": 0..1}
    """.formatted(surface, clip(context, 1400), toJson(objectMapper, cand));

        return callChatJson(system, user)
                .map(json -> {
                    try {
                        JsonNode n = objectMapper.readTree(json);
                        boolean fit = n.path("fit").asBoolean(false);
                        double conf = n.path("confidence").asDouble(0.0);
                        // 너무 애매하면 false 처리(하한선)
                        return fit && conf >= 0.55;
                    } catch (Exception e) {
                        log.warn("[AI] isDbCandidateFit parse error: {}", e.toString());
                        return false;
                    }
                })
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Integer> pickBestWordIndexFromDbCandidates(String surface, String context, List<Word> candidates) {
        if (candidates == null || candidates.isEmpty()) return Mono.just(-1);

        // 후보 최대 10개 제한
        int limit = Math.min(10, candidates.size());
        List<Map<String, Object>> cands = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            Word w = candidates.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", i);
            m.put("wordName", nz(w.getWordName()));
            m.put("definition", nz(w.getDefinition()));
            m.put("example", nz(w.getExample()));
            m.put("categories", nz(w.getWordCategory()));
            m.put("targetCode", w.getTargetCode());
            m.put("senseNo", w.getSenseNo());
            cands.add(m);
        }

        String user = """
        아래 DB 후보들 중 기사 **맥락**과 가장 잘 맞는 **한 개**의 index를 고르세요.
        - 표제어: %s
        - 맥락(앞부분만): %s
        - 후보들(JSON): %s

        반드시 다음 **JSON 객체만** 반환하세요:
        {"choice": <정수 인덱스>, "confidence": <0..1>}
        """.formatted(surface, clip(context, 1400), toJson(objectMapper, cands));

        String system = "You are a strict disambiguation engine. Respond ONLY with a JSON object.";

        return callChatJson(system, user)
                .map(json -> {
                    try {
                        JsonNode n = objectMapper.readTree(json);
                        int idx = n.path("choice").asInt(-1);
                        return (idx >= 0 && idx < limit) ? idx : -1;
                    } catch (Exception e) {
                        log.warn("[AI] pickBestWordIndexFromDbCandidates parse error: {}", e.toString());
                        return -1;
                    }
                })
                .defaultIfEmpty(-1);
    }
}
