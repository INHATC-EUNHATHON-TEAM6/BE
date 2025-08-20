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
                        JsonNode root  = objectMapper.readTree(body);
                        JsonNode items = root.path("channel").path("item");
                        if (!items.isArray() || items.size() == 0) return Mono.empty();

                        // 가장 적합한 item 고르기 (완전일치 우선, 아니면 첫번째)
                        JsonNode item = null;
                        for (JsonNode it : items) {
                            if (qq.equals(textOrNull(it, "word"))) {
                                item = it;
                                break;
                            }
                        }
                        if (item == null) item = items.get(0);

                        String lemma = textOrNull(item, "word");
                        long tc = item.path("target_code").asLong(0);
                        if (tc <= 0) return Mono.empty();

                        // search 응답의 간단한 정의/분야 (있으면 사용, 없으면 null)
                        JsonNode senseFromSearch = pickFirstSense(item);
                        String def = textOrNull(senseFromSearch, "definition");
                        String type = textOrNull(senseFromSearch, "type");
                        String exampleFromSearch = pickExample(senseFromSearch);
                        byte supNo = (byte) item.path("sup_no").asInt(0);

                        log.debug("[DICT] search hit lemma='{}' tc={} → call view.do", lemma, tc);

                        // view.do로 예문/유의어/반의어/sense_no 보강
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
    }

    private Mono<SenseInfo> fetchFirstSenseInfo(long targetCode) {
        if (targetCode <= 0) return Mono.empty();

        return dicWebClient.get()
                .uri(b -> b.path("/view.do")
                        .queryParam("key", apiKey)
                        .queryParam("req_type", "json")
                        .queryParam("method", "target_code")
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
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        JsonNode item = root.path("channel").path("item");
                        if (isMissing(item)) return Mono.empty();

                        JsonNode senseNode = pickFirstSense(item);
                        if (isMissing(senseNode)) return Mono.empty();

                        String example = pickExample(senseNode);
                        Short senseNo = toShortOrNull(senseNode.path("sense_code").asText(null));
                        List<String> synonyms = collectLexical(item, senseNode, true);
                        List<String> antonyms = collectLexical(item, senseNode, false);

                        log.debug("[DICT] view parsed tc={}, exEmpty={}, senseNo={}, syn={}, ant={}",
                                targetCode, isBlank(example), senseNo, synonyms.size(), antonyms.size());

                        return Mono.just(new SenseInfo(example, senseNo, synonyms, antonyms));
                    } catch (Exception e) {
                        log.warn("[DICT] view parse error: {}", e.toString());
                        return Mono.empty();
                    }
                });
    }

    // --- 유틸리티 메서드 ---
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