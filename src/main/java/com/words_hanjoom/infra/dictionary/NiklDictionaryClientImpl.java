package com.words_hanjoom.infra.dictionary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.words_hanjoom.domain.wordbooks.dto.request.SearchRequest;
import com.words_hanjoom.domain.wordbooks.dto.response.SearchResponse;
import com.words_hanjoom.domain.wordbooks.dto.response.ViewResponse;
import com.words_hanjoom.domain.wordbooks.dto.response.DictEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NiklDictionaryClientImpl implements NiklDictionaryClient {

    @Qualifier("dicWebClient")
    private final WebClient dicWebClient;
    private final ObjectMapper objectMapper;

    @Value("${nikl.api.key:}")
    private String apiKey;

    @jakarta.annotation.PostConstruct
    void verifyApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("nikl.api.key is missing or blank");
        }
    }

    @Override
    public Mono<SearchResponse> search(SearchRequest req) {
        // --- 필수/기본값 보정 ---
        String q = nz(req.q());
        if (q == null) return Mono.empty();

        String reqType = (nz(req.reqType()) != null) ? req.reqType() : "json";
        Integer start = (req.start() != null) ? req.start() : 1;
        Integer num = (req.num() != null) ? req.num() : 10;
        String advanced = (nz(req.advanced()) != null) ? req.advanced() : "y";

        return dicWebClient.get()
                .uri(b -> {
                    var ub = b.path("/search.do")
                            .queryParam("key", apiKey)
                            .queryParam("q", q)
                            .queryParam("req_type", reqType)
                            .queryParam("start", start)
                            .queryParam("num", num)
                            .queryParam("advanced", advanced);

                    qp(ub, "pos", req.pos());
                    qp(ub, "method", req.method());
                    qp(ub, "target", req.target());
                    qp(ub, "type1", req.type1());
                    qp(ub, "type2", req.type2());
                    qp(ub, "cat", req.cat());
                    qp(ub, "multimedia", req.multimedia());
                    qp(ub, "letter_s", req.letterS());
                    qp(ub, "letter_e", req.letterE());
                    qp(ub, "update_s", req.updateS());
                    qp(ub, "update_e", req.updateE());

                    return ub.build();
                })
                .accept(MediaType.parseMediaType("text/json"))
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> {
                    log.warn("[DICT] bodyToMono error(search): {}", e.toString(), e);
                    return Mono.empty();
                })
                .map(body -> read(body, SearchResponse.class));
    }

    @Override
    public Mono<ViewResponse> view(long targetCode) {
        return dicWebClient.get()
                .uri(b -> b.path("/view.do")
                        .queryParam("key", apiKey)
                        .queryParam("req_type", "json")
                        .queryParam("target_code", targetCode)
                        .build())
                .accept(MediaType.parseMediaType("text/json"))
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> read(body, ViewResponse.class));
    }


    @Override
    public Mono<DictEntry> quickLookup(String q) {
        String qq = nz(q);
        if (qq == null) return Mono.empty();

<<<<<<< HEAD
        // 표제어/뜻풀이/용례 포함 검색을 모두 시도하고 가장 적합한 결과를 반환
        return Mono.just(qq)
                .flatMap(word -> searchFirst(word, "exact", 1))
                .switchIfEmpty(Mono.defer(() -> searchFirst(qq, "include", 1)))
                .switchIfEmpty(Mono.defer(() -> searchFirst(wildcardMiddle(qq), "wildcard", 1)))
                .switchIfEmpty(Mono.defer(() -> searchFirst(qq, "include", 8)))
                .switchIfEmpty(Mono.defer(() -> searchFirst(qq, "include", 9)))
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("[DICT] no hit for q='{}'", qq);
                    return Mono.empty();
                }));
    }

    // ===== 개별 호출 + 파싱 =====
    private Mono<DictEntry> searchFirst(String q, String method, Integer target) {
=======
        return searchOnce(qq, "exact", 1)          // 표제어 정확히
                .switchIfEmpty(searchOnce(qq, "include", 1))
                .switchIfEmpty(searchOnce(qq, "wildcard", 1))
                .switchIfEmpty(searchOnce(qq, "include", 8))  // 뜻풀이
                .switchIfEmpty(searchOnce(qq, "include", 9)); // 용례
    }

    // ===== 개별 호출 + 파싱 =====
    // /search.do 한 번 호출해서 1개 item 파싱 → view.do로 보강 → DictEntry 조립
    private Mono<DictEntry> searchOnce(String q, String method, int target) {
        String qq = nz(q);
        if (qq == null) return Mono.empty();

        return dicWebClient.get()
                .uri(b -> b.path("/search.do")
                        .queryParam("key", apiKey)
                        .queryParam("q", qq)
                        .queryParam("req_type", "json")
                        .queryParam("advanced", "y")
                        .queryParam("method", method)   // exact/include/wildcard
                        .queryParam("target", target)   // 1:표제어, 8:뜻풀이, 9:용례
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(body -> {
                    try {
                        JsonNode root  = objectMapper.readTree(body);
                        JsonNode items = root.path("channel").path("item");
                        if (items.isMissingNode() || items.isNull()) return Mono.empty();

                        JsonNode item = items.isArray() ? items.get(0) : items;

                        String lemma = textOrNull(item, "word");
                        byte   supNo = (byte) item.path("sup_no").asInt(0);
                        long   tc    = item.path("target_code").asLong(0);

                        // search 응답에 딸려오는 간단한 sense 정보(있으면 사용)
                        JsonNode sense = item.path("sense");
                        if (sense.isArray()) sense = arrayFirst(sense);
                        String def  = textOrNull(sense, "definition");
                        String type = textOrNull(sense, "type");
                        String exampleFromSearch = textOrNull(sense, "example");

                        return fetchFirstSenseInfo(tc)
                                .defaultIfEmpty(new SenseInfo(exampleFromSearch, null, List.of(), List.of()))
                                .map(si -> DictEntry.builder()
                                        .lemma(lemma)
                                        .definition(def)
                                        .fieldType(type)
                                        .targetCode(tc)
                                        .shoulderNo(supNo)
                                        .example(!isBlank(si.example()) ? si.example() : exampleFromSearch)
                                        .senseNo(si.senseNo())
                                        .synonyms(si.synonyms())
                                        .antonyms(si.antonyms())
                                        .build());
                    } catch (Exception e) {
                        log.warn("[DICT] parse error(searchOnce): {}", e.toString());
                        return Mono.empty();
                    }
                });
    }

    /*private Mono<DictEntry> searchFirst(String q) {
>>>>>>> e2afe43 (fix before)
        String qq = nz(q);
        if (qq == null) return Mono.empty();

        return dicWebClient.get()
                .uri(b -> b.path("/search.do")
                        .queryParam("key", apiKey)
                        .queryParam("q", qq)
                        .queryParam("req_type", "json")
                        .queryParam("advanced", "y")
                        .queryParam("method", method)
                        .queryParamIfPresent("target", Optional.ofNullable(target))
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> {
                    log.warn("[DICT] searchFirst {}:{} error: {}", method, target, e.toString(), e);
                    return Mono.empty();
                })
                .flatMap(body -> {
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        JsonNode itemNode = root.path("channel").path("item");
                        if (isMissing(itemNode)) return Mono.empty();

                        // 1) item이 객체든 배열이든 모두 처리
                        List<JsonNode> candidates = new ArrayList<>();
                        if (itemNode.isArray()) {
                            itemNode.forEach(candidates::add);
                        } else if (itemNode.isObject()) {
                            candidates.add(itemNode);
                        } else {
                            return Mono.empty();
                        }

                        // 2) 표제어 추출 헬퍼
                        java.util.function.Function<JsonNode, String> getLemma = it ->
                                Optional.ofNullable(textOrNull(it, "word"))
                                        .orElse(textOrNull(it.path("word_info"), "word"));

                        // 3) 완전일치 우선 선택 (word 혹은 word_info.word)
                        JsonNode item = candidates.stream()
                                .filter(it -> qq.equals(getLemma.apply(it)))
                                .findFirst()
                                .orElse(candidates.get(0));

                        String lemma = getLemma.apply(item);

                        long tc = item.path("target_code").asLong(0);
                        if (tc <= 0) return Mono.empty();

                        JsonNode senseFromSearch = pickFirstSense(item);
                        String def = textOrNull(senseFromSearch, "definition");
                        String type = textOrNull(senseFromSearch, "type");
                        String exampleFromSearch = pickExample(senseFromSearch);
                        byte supNo = (byte) item.path("sup_no").asInt(0);

                        // view.do로 보강
                        return fetchFirstSenseInfo(tc)
                                .defaultIfEmpty(new SenseInfo(null, null, List.of(), List.of()))
                                .map(si -> {
                                    String finalExample = !isBlank(si.example()) ? si.example() : exampleFromSearch;
                                    return DictEntry.builder()
                                            .lemma(lemma)
                                            .definition(def)
                                            .fieldType(type)
                                            .targetCode(tc)
                                            .shoulderNo(supNo)
                                            .example(finalExample)
                                            .senseNo(si.senseNo())
                                            .synonyms(si.synonyms())
                                            .antonyms(si.antonyms())
                                            .build();
                                });
                    } catch (Exception e) {
                        log.warn("[DICT] parse error(searchFirst {}:{}): {}", method, target, e.toString(), e);
                        return Mono.empty();
                    }
                });
<<<<<<< HEAD
    }

=======
    }*/

    // NIKL view.do에서 예문/유의어/반의어/senseNo 파싱
>>>>>>> e2afe43 (fix before)
    private Mono<SenseInfo> fetchFirstSenseInfo(long targetCode) {
        if (targetCode <= 0) return Mono.empty();

        log.info("[DICT] fetchFirstSenseInfo called, tc={}", targetCode);

        return dicWebClient.get()
                .uri(b -> b.path("/view.do")
                        .queryParam("key", apiKey)
                        .queryParam("req_type", "json")
                        .queryParam("target_code", targetCode)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(e -> {
                    log.warn("[DICT] bodyToMono error(view): {}", e.toString());
                    return Mono.empty();
                })
                .flatMap(body -> {
                    log.info("[DICT] view.do body for tc={}: {}", targetCode, body);
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        JsonNode item = root.path("channel").path("item");
                        if (isMissing(item)) return Mono.empty();
<<<<<<< HEAD

                        JsonNode senseNode = pickFirstSense(item);
                        if (isMissing(senseNode)) return Mono.empty();

                        String example = pickExample(senseNode);
                        Short senseNo = toShortOrNull(senseNode.path("sense_code").asText(null));
                        List<String> synonyms = collectLexical(item, senseNode, true);
                        List<String> antonyms = collectLexical(item, senseNode, false);

                        log.debug("[DICT] view parsed tc={}, exEmpty={}, senseNo={}, syn={}, ant={}",
                                targetCode, isBlank(example), senseNo, synonyms.size(), antonyms.size());

=======
                        if (item.isArray() && item.size() > 0) {
                            item = item.get(0); // ✅ 배열일 경우 첫 번째 아이템 선택
                        }

                        JsonNode sense = pickFirstSense(item); // word_info → pos_info[0] → comm_pattern_info[0] → sense_info[0]
                        if (isMissing(sense)) return Mono.empty();

                        // ✅ 예문: example_info 배열 → 첫 번째 example
                        String example = null;
                        JsonNode exList = sense.path("example_info");
                        if (exList.isArray() && exList.size() > 0) {
                            example = textOrNull(exList.get(0), "example");
                        }

                        // ✅ sense_no (없으면 sense_code 사용)
                        Short senseNo = toShortOrNull(textOrNull(sense, "sense_no"));
                        if (senseNo == null && sense.has("sense_code")) {
                            senseNo = (short) sense.get("sense_code").asInt();
                        }

                        // ✅ 유의어 / 반의어
                        List<String> synonyms = new ArrayList<>();
                        List<String> antonyms = new ArrayList<>();

                        JsonNode lexInfos = sense.path("lexical_info");
                        if (lexInfos.isArray()) {
                            for (JsonNode lex : lexInfos) {
                                String type = textOrNull(lex, "type");
                                String word = textOrNull(lex, "word");
                                if (isBlank(word)) continue;

                                if ("유의어".equals(type) || "동의어".equals(type)) {
                                    synonyms.add(word);
                                } else if ("반의어".equals(type) || "반대말".equals(type)) {
                                    antonyms.add(word);
                                }
                            }
                        }

                        log.debug("[DICT] view parsed tc={}, senseNo={}, syn={}, ant={}, ex={}",
                                targetCode, senseNo, synonyms, antonyms, example);

>>>>>>> e2afe43 (fix before)
                        return Mono.just(new SenseInfo(example, senseNo, synonyms, antonyms));
                    } catch (Exception e) {
                        log.warn("[DICT] view parse error: {}", e.toString());
                        return Mono.empty();
                    }
                });
    }

<<<<<<< HEAD
    // --- 유틸리티 메서드 ---
=======

    private List<String> collectRelatedWordsFromLexical(JsonNode sense, boolean wantSynonym) {
        List<String> out = new ArrayList<>();
        JsonNode lex = sense.path("lexical_info");
        if (!lex.isArray()) return out;
        for (JsonNode n : lex) {
            String type = textOrNull(n, "type");
            String word = textOrNull(n, "word");
            if (isBlank(word)) continue;
            if (wantSynonym) {
                if ("유의어".equals(type) || "동의어".equals(type)) out.add(word);
            } else {
                if ("반의어".equals(type) || "반대말".equals(type)) out.add(word);
            }
        }
        return out;
    }

>>>>>>> e2afe43 (fix before)
    private static boolean isMissing(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull();
    }

    private <T> T read(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (Exception e) {
            throw new RuntimeException("DICT_PARSE_ERROR: " + e.getMessage(), e);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        if (isMissing(node)) return null;
        JsonNode n = node.path(field);
        String s = n.asText(null);
        return isBlank(s) ? null : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

<<<<<<< HEAD
=======

    private String findFirstByKeys(JsonNode root, String... keys) {
        if (root == null || root.isMissingNode() || root.isNull()) return null;
        if (keys == null || keys.length == 0) return null;

        Set<String> keySet = new HashSet<>(Arrays.asList(keys));
        Deque<JsonNode> q = new ArrayDeque<>();
        q.add(root);

        while (!q.isEmpty()) {
            JsonNode cur = q.poll();
            if (cur == null || cur.isMissingNode() || cur.isNull()) continue;

            if (cur.isObject()) {
                var it = cur.fields();
                while (it.hasNext()) {
                    var e = it.next();
                    String name = e.getKey();
                    JsonNode child = e.getValue();

                    if (keySet.contains(name)) {
                        String v = child.asText(null);
                        if (v != null && !v.isBlank()) return v;
                    }
                    q.add(child);
                }
            } else if (cur.isArray()) {
                for (JsonNode child : cur) q.add(child);
            }
        }
        return null;
    }

    // 문자열 공백 방어
>>>>>>> e2afe43 (fix before)
    private static String nz(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static void qp(org.springframework.web.util.UriBuilder ub, String name, Optional<?> opt) {
        if (opt == null) return;
        opt.filter(v -> !(v instanceof String s) || !s.isBlank())
                .ifPresent(v -> ub.queryParam(name, v));
    }

    private static Short toShortOrNull(String s) {
        if (isBlank(s)) return null;
        try { return Short.valueOf(s); } catch (Exception e) { return null; }
    }

    private static String wildcardMiddle(String q) {
        if (q == null) return null;
        String qq = q.trim();
        if (qq.length() <= 1) return qq + "*";
        int mid = qq.length() / 2;
        return qq.substring(0, mid) + "*" + qq.substring(mid);
    }

    private static boolean isSynonymType(String type) {
        if (type == null) return false;
        String t = type.replace(" ", "");
        return t.contains("유의") || t.contains("비슷") || t.contains("동의");
    }

    private static boolean isAntonymType(String type) {
        if (type == null) return false;
        String t = type.replace(" ", "");
        return t.contains("반의") || t.contains("반대") || t.contains("상반");
    }

<<<<<<< HEAD
    private static JsonNode pickFirstSense(JsonNode item) {
        if (isMissing(item)) return null;
        JsonNode wi = item.path("word_info");
        JsonNode posArr = wi.path("pos_info");
        if (posArr.isArray() && posArr.size() > 0) {
            JsonNode commArr = posArr.path(0).path("comm_pattern_info");
            if (commArr.isArray() && commArr.size() > 0) {
                JsonNode senses = commArr.path(0).path("sense_info");
                if (senses.isArray() && senses.size() > 0) {
                    return senses.get(0);
                } else if (senses.isObject()) {
                    return senses;
                }
            }
        }
        return item.path("sense");
    }

    private static String pickExample(JsonNode sense) {
        if (isMissing(sense)) return null;

        JsonNode exList = sense.path("example_info");
        if (exList.isArray() && exList.size() > 0) {
            for (JsonNode exNode : exList) {
                String ex = textOrNull(exNode, "example");
                if (!isBlank(ex)) {
                    return ex;
                }
            }
        }
        return textOrNull(sense, "example");
=======
    private JsonNode arrayFirst(JsonNode n) {
        return (n != null && n.isArray() && n.size() > 0) ? n.get(0) : n;
    }

    private JsonNode pickFirstSense(JsonNode item) {
        JsonNode wi = item.path("word_info");
        if (isMissing(wi)) return MissingNode.getInstance();

        JsonNode posInfos = wi.path("pos_info");
        if (posInfos.isArray()) {
            for (JsonNode pos : posInfos) {
                JsonNode comms = pos.path("comm_pattern_info");
                if (comms.isArray()) {
                    for (JsonNode comm : comms) {
                        JsonNode senses = comm.path("sense_info");
                        if (senses.isArray() && senses.size() > 0) {
                            // ✅ 유의어/반의어/예문이 있는 sense 우선 반환
                            for (JsonNode s : senses) {
                                if (s.has("lexical_info") || s.has("example_info")) {
                                    return s;
                                }
                            }
                            return senses.get(0); // fallback: 첫 sense
                        }
                    }
                }
            }
        }
        return MissingNode.getInstance();
    }

    /** sense_info에서 예문 하나 고르기 (우선순위: example_info[].example → 기타 백업 키) */
    private List<String> pickExamples(JsonNode sense) {
        List<String> examples = new ArrayList<>();
        JsonNode exList = sense.path("example_info");
        if (exList.isArray()) {
            for (JsonNode ex : exList) {
                String v = textOrNull(ex, "example");
                if (!isBlank(v)) examples.add(v);
            }
        }
        return examples;
    }


    private String extractExampleFromSearch(JsonNode sense) {
        if (sense == null || sense.isMissingNode() || sense.isNull()) return null;

        // 가장 단순 키
        String ex = textOrNull(sense, "example");
        if (isBlank(ex)) {
            // 배열 구조 1
            JsonNode exList = sense.path("example_info");
            if (exList.isArray() && exList.size() > 0) {
                ex = textOrNull(exList.get(0), "example");
            }
        }
        if (isBlank(ex)) {
            // 배열 구조 2
            ex = textOrNull(sense.path("sense_example").path(0), "example");
        }
        if (isBlank(ex)) {
            // 배열 구조 3
            ex = textOrNull(sense.path("example_list").path(0), "text");
        }
        return ex;
    }

    private JsonNode pickFirstSenseFromSearch(JsonNode item) {
        if (item == null || item.isMissingNode() || item.isNull()) {
            return MissingNode.getInstance();
        }

        // 일반적인 search.do 응답: item.sense (객체 또는 배열)
        JsonNode sense = item.path("sense");
        if (sense.isArray()) {
            if (sense.size() > 0) return sense.get(0);
        } else if (!sense.isMissingNode() && !sense.isNull() && sense.hasNonNull("definition")) {
            return sense;
        }

        // (드물지만) word_info 경로로 내려오는 경우 대비 — 안전 백업
        JsonNode posInfo = item.path("word_info").path("pos_info");
        if (posInfo.isArray() && posInfo.size() > 0) {
            JsonNode cpis = posInfo.get(0).path("comm_pattern_info");
            if (cpis.isArray() && cpis.size() > 0) {
                JsonNode sInfos = cpis.get(0).path("sense_info");
                if (sInfos.isArray() && sInfos.size() > 0) {
                    return sInfos.get(0);
                }
            }
        }

        return MissingNode.getInstance();
>>>>>>> e2afe43 (fix before)
    }

    private static List<String> collectLexical(JsonNode item, JsonNode sense, boolean wantSyn) {
        Set<String> out = new LinkedHashSet<>();

        JsonNode senseLexical = sense.path("lexical_info");
        if (senseLexical.isArray()) {
            for (JsonNode node : senseLexical) {
                String type = textOrNull(node, "type");
                if (wantSyn && isSynonymType(type) || (!wantSyn && isAntonymType(type))) {
                    String word = textOrNull(node, "word");
                    if (!isBlank(word)) {
                        out.add(word);
                    }
                }
            }
        }

        JsonNode wordInfo = item.path("word_info");
        if (!isMissing(wordInfo)) {
            JsonNode relationInfo = wordInfo.path("relation_info");
            if (relationInfo.isArray()) {
                for (JsonNode node : relationInfo) {
                    String type = textOrNull(node, "type");
                    if (wantSyn && isSynonymType(type) || (!wantSyn && isAntonymType(type))) {
                        String word = textOrNull(node, "word");
                        if (!isBlank(word)) {
                            out.add(word);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }

    record SenseInfo(String example, Short senseNo, List<String> synonyms, List<String> antonyms) {}
}