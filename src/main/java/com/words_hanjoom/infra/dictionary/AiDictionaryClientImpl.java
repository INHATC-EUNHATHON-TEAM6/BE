// src/main/java/com/words_hanjoom/infra/dictionary/AiDictionaryClientImpl.java
package com.words_hanjoom.infra.dictionary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.words_hanjoom.domain.wordbooks.dto.ai.SenseCandidate;
import com.words_hanjoom.domain.wordbooks.dto.ai.SenseChoiceResult;
import com.words_hanjoom.domain.wordbooks.dto.response.AiPickResult;
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
                        Map.of("role", "user", "content", toJson(user))
                )
        );

        return openAiWebClient.post()
                .uri("/chat/completions")
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
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
    // ===== 공통 헬퍼 =====

    private SenseChoiceResult heuristicPick(String context, List<SenseCandidate> cands) {
        String ctx = Optional.ofNullable(context).orElse("").toLowerCase(Locale.ROOT);

        int bestIdx = 0, bestScore = Integer.MIN_VALUE;
        for (var c : cands) {
            int s = 0;
            if (c.definition() != null) s += overlap(ctx, c.definition().toLowerCase(Locale.ROOT));
            if (c.example() != null) s += overlap(ctx, c.example().toLowerCase(Locale.ROOT));
            if (c.categories() != null && !c.categories().isBlank()) {
                var firstCat = c.categories().split(",")[0].trim().toLowerCase(Locale.ROOT);
                if (!firstCat.isBlank() && ctx.contains(firstCat)) s += 2;
            }
            if (s > bestScore) {
                bestScore = s;
                bestIdx = c.idx();
            }
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

    @Override
    public Mono<Integer> pickBestWordIndexFromDbCandidates(
            String surface, String context, List<Word> candidates) {

        if (candidates == null || candidates.isEmpty()) return Mono.just(-1);

        int limit = Math.min(10, candidates.size());
        List<Map<String, Object>> cands = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Word w = candidates.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index",       i);
            m.put("wordName",    nz(w.getWordName()));
            m.put("definition",  nz(w.getDefinition()));
            m.put("example",     nz(w.getExample()));
            m.put("categories",  nz(w.getWordCategory()));
            m.put("targetCode",  w.getTargetCode());
            m.put("senseNo",     w.getSenseNo());
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

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static List<String> strList(JsonNode arr) {
        if (arr == null || !arr.isArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonNode e : arr) if (e != null && e.isTextual()) out.add(e.asText());
        return out;
    }

    private static String toJson(ObjectMapper om, Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
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
                        String lemma = nz(node.path("lemma").asText(null));
                        String definition = nz(node.path("definition").asText(null));
                        String example = nz(node.path("example").asText(""));
                        List<String> synonyms = strList(node.get("synonyms"));
                        List<String> antonyms = strList(node.get("antonyms"));
                        List<String> cats = strList(node.get("categories"));
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
                                .shoulderNo((byte) 0)
                                .build();
                        return Mono.just(entry);
                    } catch (Exception e) {
                        log.warn("[AI] defineFromAi parse error: {}", e.toString());
                        return Mono.empty();
                    }
                });
    }

    @Override
    public Mono<AiPickResult> pickBestFromNiklCandidatesV2(
            String surface, String context, List<DictEntry> cands) {

        if (cands == null || cands.isEmpty()) return Mono.empty();

        // 후보 JSON (LLM 입력)
        ArrayNode arr = objectMapper.createArrayNode();
        for (int i = 0; i < cands.size(); i++) {
            DictEntry e = cands.get(i);
            ObjectNode n = objectMapper.createObjectNode();
            n.put("idx", i);
            n.put("target_code", e.getTargetCode());
            n.put("gloss", nz(e.getDefinition()));
            n.put("type", nz(e.getCategories()));
            arr.add(n);
        }
        String candidatesJson = arr.toString();

        String system = """
        너는 한국어 다의어 판별기다. 
        주어진 문맥(context)에 가장 적합한 뜻 하나를 후보 목록 중에서 고른다.
        후보는 각 항목에 target_code가 포함된다.
        출력은 반드시 JSON 한 줄로만 하고, 다른 텍스트를 섞지 마라.
        스키마:
        { "target_code": <long>, "sense_no": <int|null>, "confidence": <0.0~1.0> }
        제약:
        - target_code는 반드시 후보 목록의 값 중 하나여야 한다.
        - sense_no를 모르면 null 또는 0을 넣어라.
        - 선택이 불가하면 target_code에 null을 넣어라.
        """;

        String user = """
        surface: "%s"
        context: "%s"
        candidates: %s
        """.formatted(surface, nz(context), candidatesJson);

        return callChatJson(system, user)
                .map(this::parseAiPickResultStrict)
                // 2차 검증: 선택 후보가 문맥에 맞는지 일반화 fit 검사
                .flatMap(res -> {
                    // 1) 후보 인덱스 계산
                    int tmp = (res.getTargetCode() != null)
                            ? indexOfTarget(cands, res.getTargetCode())
                            : normalizeIdx(res.getIdx(), cands.size()); // res.getIdx()는 1-base일 수 있음

                    // 2) 범위 클램프
                    tmp = clamp0(tmp, cands.size());

                    // 3) 람다에서 쓸 final 사본
                    final int chosenIdx = tmp;

                    DictEntry chosen = cands.get(chosenIdx);

                    return isNiklCandidateFit(surface, context, chosen).flatMap(fit -> {
                        if (fit) {
                            res.setIdx(chosenIdx + 1);
                            res.setTargetCode(chosen.getTargetCode());
                            if (res.getSenseNo() == null) res.setSenseNo(chosen.getSenseNo());
                            return Mono.just(res);
                        }

                        return reactor.core.publisher.Flux.range(0, cands.size())
                                .filter(i -> i != chosenIdx)
                                .concatMap(i -> isNiklCandidateFit(surface, context, cands.get(i))
                                        .map(ok -> ok ? i : -1))
                                .filter(i -> i >= 0)
                                .next()
                                .defaultIfEmpty(chosenIdx)
                                .map(bestIdx -> {
                                    DictEntry best = cands.get(bestIdx);
                                    res.setIdx(bestIdx + 1);
                                    res.setTargetCode(best.getTargetCode());
                                    res.setSenseNo(best.getSenseNo());
                                    return res;
                                });
                    });
                })
                .onErrorResume(err -> {
                    log.warn("[AI] V2 parse/validate failed → fallback index parser", err);
                    return callLegacyIndexIfNeeded(surface, context, cands);
                });
    }

    private int indexOfTarget(List<DictEntry> cands, Long targetCode) {
        if (targetCode == null) return -1;
        for (int i = 0; i < cands.size(); i++) {
            if (Objects.equals(cands.get(i).getTargetCode(), targetCode)) return i;
        }
        return -1;
    }

    private static int clamp0(int idx, int size) {
        if (size <= 0) return 0;
        if (idx < 0) return 0;
        if (idx >= size) return size - 1;
        return idx;
    }

    private Mono<Boolean> isNiklCandidateFit(String surface, String context, DictEntry e) {
        String system = "You are a strict disambiguation engine. Respond ONLY with a JSON object.";
        Map<String,Object> cand = Map.of(
                "lemma",       nz(e.getLemma()),
                "definition",  nz(e.getDefinition()),
                "example",     nz(e.getExample()),
                "categories",  nz(e.getCategories()),
                "targetCode",  e.getTargetCode(),
                "senseNo",     e.getSenseNo()
        );

        String user = """
        "표제어: "%s"
        아래 기사 **문맥의 전체적인 주제**와 **주변 단어들**을 종합적으로 고려하여, 가장 의미적으로 부합하는 단어의 뜻을 후보 목록에서 고르세요. 특히, **문맥에 나타난 동사, 명사 등의 연관성**에 집중하세요. 예를 들어 '장'이 '소화기관'을 의미하는 문맥에는 '소화', '건강', '세균' 같은 단어가 포함될 수 있습니다.
        
        맥락:
        %s
        
        후보들:
        %s
        
        최종적으로 가장 적합한 **하나**의 후보를 선택하세요.
    """.formatted(surface, clip(context, 1400), toJson(objectMapper, cand));

        return callChatJson(system, user)
                .map(json -> {
                    try {
                        JsonNode n = objectMapper.readTree(json);
                        boolean fit = n.path("fit").asBoolean(false);
                        double  cf  = n.path("confidence").asDouble(0.0);
                        return fit && cf >= 0.55;   // 임계값은 운영하며 조정
                    } catch (Exception ex) {
                        log.warn("[AI] isNiklCandidateFit parse error: {}", ex.toString());
                        return false;
                    }
                })
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<AiPickResult> pickBestFromNiklCandidates(
            String surface, String context, List<DictEntry> candidates) {

        if (candidates == null || candidates.isEmpty()) return Mono.empty();

        // 토큰/지연 관리: 최대 8개만 LLM에 전달
        final int limit = Math.min(8, candidates.size());
        final List<Map<String, Object>> cands = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            DictEntry e = candidates.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", i);
            m.put("lemma",       e.getLemma());
            m.put("definition",  nz(e.getDefinition()));
            m.put("example",     nz(e.getExample()));
            m.put("categories",  nz(e.getCategories()));
            m.put("targetCode",  e.getTargetCode());
            m.put("senseNo",     e.getSenseNo());
            cands.add(m);
        }

        final String user = """
        "표제어: "%s"
        아래 기사 **문맥의 전체적인 주제**와 **주변 단어들**을 종합적으로 고려하여, 가장 의미적으로 부합하는 단어의 뜻을 후보 목록에서 고르세요. 특히, **문맥에 나타난 동사, 명사 등의 연관성**에 집중하세요. 예를 들어 '장'이 '소화기관'을 의미하는 문맥에는 '소화', '건강', '세균' 같은 단어가 포함될 수 있습니다.
        
        맥락:
        %s
        
        후보들:
        %s
        
        최종적으로 가장 적합한 **하나**의 후보를 선택하세요.
        """.formatted(surface, clip(context, 1400), toJson(objectMapper, cands));

        final String system = "You are a strict disambiguation engine. Respond ONLY with a JSON object.";

        return callChatJson(system, user)
                .flatMap(json -> {
                    try {
                        JsonNode n = objectMapper.readTree(json);
                        final int choice = n.path("choice").asInt(-1);
                        final Double conf = (n.hasNonNull("confidence")) ? n.get("confidence").asDouble() : null;

                        if (choice < 0 || choice >= limit) return Mono.empty();

                        // idx는 1-base로 보관 (로그/디버깅 편의)
                        DictEntry chosen = candidates.get(choice);
                        AiPickResult r = new AiPickResult();
                        r.setIdx(choice + 1);
                        r.setTargetCode(chosen.getTargetCode());
                        r.setSenseNo(chosen.getSenseNo());
                        r.setConfidence(conf);
                        return Mono.just(r);
                    } catch (Exception e) {
                        log.warn("[AI] pickBestFromNiklCandidates parse error: {}", e.toString());
                        return Mono.empty();
                    }
                });
    }


    private Mono<AiPickResult> callLegacyIndexIfNeeded(
            String surface, String context, List<DictEntry> cands) {

        return pickBestFromNiklCandidates(surface, context, cands)
                .map(r -> {
                    // r.idx는 1-base일 수 있음 → 0-base로 정규화
                    int idx0 = normalizeIdx(r.getIdx(), cands.size());
                    DictEntry chosen = (cands.isEmpty() ? null : cands.get(idx0));

                    if ((r.getTargetCode() == null || r.getTargetCode() == 0L) && chosen != null) {
                        r.setTargetCode(chosen.getTargetCode());
                    }
                    if ((r.getSenseNo() == null || r.getSenseNo() == 0) && chosen != null) {
                        r.setSenseNo(chosen.getSenseNo() == null ? 0 : chosen.getSenseNo());
                    }

                    // 편의상 1-base 유지
                    r.setIdx(idx0 + 1);
                    return r;
                })
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    // LLM이 아무것도 못 줬을 때: 0번째를 기본값으로
                    AiPickResult r = new AiPickResult();
                    if (!cands.isEmpty()) {
                        DictEntry chosen = cands.get(0);
                        r.setTargetCode(chosen.getTargetCode());
                        r.setSenseNo(chosen.getSenseNo() == null ? 0 : chosen.getSenseNo());
                    }
                    r.setIdx(1); // 1-base
                    return r;
                }));
    }


    private int normalizeIdx(Integer oneBase, int size) {
        if (size <= 0) return 0;
        if (oneBase == null) return 0;
        int z = oneBase - 1;
        if (z < 0) return 0;
        if (z >= size) return size - 1;
        return z;
    }

    private AiPickResult parseAiPickResultStrict(String content) {
        try {
            JsonNode n = objectMapper.readTree(content);
            AiPickResult r = new AiPickResult();

            if (n.has("idx") && !n.get("idx").isNull()) {
                r.setIdx(n.get("idx").asInt());
            }
            if (n.has("target_code") && !n.get("target_code").isNull()) {
                JsonNode tc = n.get("target_code");
                r.setTargetCode(tc.isNumber() ? tc.asLong() : Long.valueOf(tc.asText()));
            }
            if (n.has("sense_no") && !n.get("sense_no").isNull()) {
                r.setSenseNo(n.get("sense_no").asInt());
            }
            if (n.has("confidence") && !n.get("confidence").isNull()) {
                r.setConfidence(n.get("confidence").asDouble());
            }

            // 최소한 target_code 또는 idx 중 하나는 있어야 함
            if (r.getTargetCode() == null && r.getIdx() == null) {
                throw new IllegalArgumentException("AI result must contain idx or target_code");
            }
            return r;
        } catch (Exception e) {
            throw new RuntimeException("AI JSON parse error: " + content, e);
        }
    }

    @Override
    public Mono<Boolean> isDbCandidateFit(String surface, String context, Word c) {
        String system = "You are a strict disambiguation engine. Respond ONLY with a JSON object.";
        Map<String, Object> cand = Map.of(
                "wordName", nz(c.getWordName()),
                "definition", nz(c.getDefinition()),
                "example", nz(c.getExample()),
                "categories", nz(c.getWordCategory()),
                "targetCode", c.getTargetCode(),
                "senseNo", c.getSenseNo()
        );
        String user = """
        "표제어: "%s"
        아래 후보 뜻과 **주어진 기사 문맥**의 **주제 및 내용**이 의미적으로 일치하는지 **'예', '아니오'**로 판단하세요.
        
        - 후보 뜻:
          - 정의: %s
          - 예문: %s
          - 분야: %s
        
        - 문맥:
        %s
        
        다음 JSON만 반환하세요:
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

}

