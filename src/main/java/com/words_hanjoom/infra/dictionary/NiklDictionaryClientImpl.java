package com.words_hanjoom.infra.dictionary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
        if (q == null) return Mono.empty(); // q 없으면 검색 안 함

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

                    // --- String (nullable) ---
                    qp(ub, "pos", req.pos());  // req.pos() == Optional<String>

                    // --- Optional<String> ---
                    qp(ub, "method", req.method());
                    qp(ub, "target", req.target());
                    qp(ub, "type1", req.type1());
                    qp(ub, "type2", req.type2());
                    qp(ub, "cat", req.cat());
                    qp(ub, "multimedia", req.multimedia());

                    // --- Optional<Integer> (or Long) ---
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
        // 내부에서 폴백 단계 전부 처리
        return searchFirst(q);
    }

    // ===== 개별 호출 + 파싱 =====
    private Mono<DictEntry> searchOnce(String submitQ, String scoreQ, String method, Integer target) {
        // 디버그용 최종 URI
        String dbg = org.springframework.web.util.UriComponentsBuilder
                .fromPath("/search.do")
                .queryParam("key", "****" + apiKey.substring(Math.max(0, apiKey.length() - 4)))
                .queryParam("q", submitQ)
                .queryParam("req_type", "json")
                .queryParam("advanced", "y")
                .queryParam("method", method)
                .queryParam("target", target)
                .build()
                .encode(java.nio.charset.StandardCharsets.UTF_8)
                .toUriString();
        log.debug("[DICT] FINAL URI {}", dbg);

        return dicWebClient.get()
                .uri(b -> {
                    var ub = b.path("/search.do")
                            .queryParam("key", apiKey)
                            .queryParam("q", submitQ)
                            .queryParam("req_type", "json")
                            .queryParam("advanced", "y")
                            .queryParam("method", method);
                    if (target != null) ub.queryParam("target", target);
                    return ub.build();
                })
                .accept(MediaType.parseMediaType("text/json"))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(7))
                .flatMap(body -> {
                    try {
                        JsonNode root  = objectMapper.readTree(body);
                        JsonNode items = root.at("/channel/item");
                        if (items.isMissingNode() || items.isNull()) return Mono.empty();

                        Iterable<JsonNode> iterable = items.isArray() ? items : java.util.List.of(items);
                        final String qNorm = normKey(scoreQ);

                        return Flux.fromIterable(iterable)
                                .sort((a, b) -> Integer.compare(
                                        matchScore(b, qNorm), matchScore(a, qNorm)))
                                .next()
                                .flatMap(itemNode -> {
                                    JsonNode senseNode = itemNode.path("sense");
                                    if (senseNode.isArray()) senseNode = senseNode.path(0);
                                    if (senseNode.isMissingNode() || senseNode.isNull()) return Mono.empty();

                                    String lemma = textOrNull(itemNode, "word");        // 표제어(예: 노동조합법)
                                    String def   = textOrNull(senseNode, "definition");
                                    String type  = textOrNull(senseNode, "type");
                                    byte   supNo = (byte) itemNode.path("sup_no").asInt(0);
                                    long   tcRaw = itemNode.path("target_code").asLong(0);
                                    Long   targetCode = (tcRaw > 0) ? tcRaw : null;
                                    String exampleFromSearch = textOrNull(senseNode, "example");

                                    return fetchFirstSenseInfo(tcRaw)
                                            .defaultIfEmpty(new SenseInfo(exampleFromSearch, null))
                                            .map(si -> {
                                                String finalExample = (si.example() != null && !si.example().isBlank())
                                                        ? si.example() : exampleFromSearch;
                                                Short finalSenseNo = si.senseNo();
                                                log.debug("[DICT] chose lemma={}, sup={}, tc={}, senseNo={}, via {}:{}",
                                                        lemma, supNo, targetCode, finalSenseNo, method, target);
                                                return new DictEntry(lemma, def, type, targetCode, supNo, finalExample, finalSenseNo);
                                            });
                                });
                    } catch (Exception e) {
                        log.warn("[DICT] parse error(searchOnce {}:{}): {}", method, target, e.toString(), e);
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.warn("[DICT] searchOnce {}:{} error: {}", method, target, e.toString(), e);
                    return Mono.empty();
                });
    }

    private Mono<DictEntry> searchFirst(String q) {
        String qq = nz(q);
        if (qq == null) return Mono.empty();

        // 와일드카드 후보들
        String wcMid  = wildcardMiddle(qq);       // 기념*촬영
        String wcPre  = "*" + qq;                 // *기념촬영
        String wcSuf  = qq + "*";                 // 기념촬영*
        String wcBoth = "*" + qq + "*";           // *기념촬영*

        return searchOnce(qq, qq, "exact",   1)
                .switchIfEmpty(searchOnce(qq, qq, "include", 1))
                .switchIfEmpty(searchOnce(wcMid,  qq, "wildcard", 1))
                .switchIfEmpty(searchOnce(wcPre,  qq, "wildcard", 1))
                .switchIfEmpty(searchOnce(wcSuf,  qq, "wildcard", 1))
                .switchIfEmpty(searchOnce(wcBoth, qq, "wildcard", 1))
                // 정의(8)와 용례(9)도 털어보기 — 약칭/표기변형 회수용
                .switchIfEmpty(searchOnce(qq, qq, "include", 8))
                .switchIfEmpty(searchOnce(qq, qq, "include", 9))
                // 최종 노히트 로깅
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("[DICT] no hit for q='{}'", qq);
                    return Mono.empty();
                }));
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
                .accept(MediaType.parseMediaType("text/json"))
                .retrieve()
                .bodyToMono(String.class)
                .switchIfEmpty(Mono.empty())
                .onErrorResume(e -> {
                    log.warn("[DICT] bodyToMono error(view): {}", e.toString());
                    return Mono.empty();
                })
                .flatMap(body -> {
                    try {
                        // ▶ 디버그: 원문 확인(너무 길면 자름)
                        if (log.isDebugEnabled()) {
                            String trimmed = body.length() > 2000 ? body.substring(0, 2000) + " …(truncated)" : body;
                            log.debug("[DICT] view raw for {} :: {}", targetCode, trimmed);
                        }

                        JsonNode root = objectMapper.readTree(body);
                        JsonNode item = root.path("channel").path("item");
                        if (item.isMissingNode() || item.isNull()) return Mono.empty();
                        if (item.isArray()) item = item.path(0);           // item이 배열이면 첫번째

                        // sense는 단일/배열 모두 처리
                        JsonNode sense = item.path("sense");
                        if (sense.isArray()) sense = sense.path(0);

                        // 예문 후보들 스캔
                        String example =
                                textOrNull(sense, "example")
                                ; if (isBlank(example)) example = textOrNull(sense.path("examples").path(0), "example");
                        if (isBlank(example)) example = textOrNull(sense.path("example_list").path(0), "text");
                        if (isBlank(example)) example = textOrNull(sense.path("sense_example").path(0), "example");

                        // sense_no 여러 경로에서 탐색
                        String senseNoStr =
                                textOrNull(sense, "sense_no");
                        if (isBlank(senseNoStr)) senseNoStr = textOrNull(sense, "sense_order");
                        if (isBlank(senseNoStr)) senseNoStr = findFirstByKeys(item, "sense_no", "sense_order"); // 🔁 백업: item 하위 전체 탐색

                        Short senseNo = toShortOrNull(senseNoStr);

                        log.debug("[DICT] view parsed tc={}, senseNo={}, exEmpty={}", targetCode, senseNo, isBlank(example));
                        return Mono.just(new SenseInfo(example, senseNo));
                    } catch (Exception e) {
                        log.warn("[DICT] view parse error: {}", e.toString());
                        return Mono.empty();
                    }
                });
    }

    private static String findFirstByKeys(JsonNode node, String... keys) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        for (String k : keys) {
            JsonNode n = node.get(k);
            if (n != null && !n.isMissingNode() && !n.isNull()) {
                String v = n.asText(null);
                if (v != null && !v.isBlank()) return v;
            }
        }
        // 자식 재귀 탐색
        var fields = node.fields();
        while (fields.hasNext()) {
            var e = fields.next();
            String v = findFirstByKeys(e.getValue(), keys);
            if (v != null && !v.isBlank()) return v;
        }
        // 배열 재귀 탐색
        if (node.isArray()) {
            for (JsonNode child : node) {
                String v = findFirstByKeys(child, keys);
                if (v != null && !v.isBlank()) return v;
            }
        }
        return null;
    }

    private record SenseInfo(String example, Short senseNo) {
    }

    private <T> T read(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (Exception e) {
            throw new RuntimeException("DICT_PARSE_ERROR: " + e.getMessage(), e);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode n = node.path(field);
        if (n.isMissingNode() || n.isNull()) {
            return null;
        }
        String s = n.asText(null);
        return isBlank(s) ? null : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // 문자열 공백 방어
    private static String nz(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    // Optional<?> 안전하게 queryParam에 붙여주는 유틸
    private static void qp(org.springframework.web.util.UriBuilder ub, String name, Optional<?> opt) {
        if (opt == null) return; // Optional 자체 null 방지
        opt.filter(v -> !(v instanceof String s) || !s.isBlank()) // String일 경우 공백 필터
                .ifPresent(v -> ub.queryParam(name, v));               // queryParam은 Object 받아서 Integer도 OK
    }

    private static Short toShortOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Short.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    // (1) 유틸: 공백/정규화
    private static String normKey(String s) {
        if (s == null) return null;
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC)
                .replaceAll("\\s+", "");
    }

    // (2) 유틸: 매칭 점수 (3>2>1)
    private static int matchScore(JsonNode itemNode, String qNorm) {
        String w = textOrNull(itemNode, "word");
        if (w == null || qNorm == null) return 0;
        String wn = normKey(w);
        if (wn.equals(qNorm)) return 3;                           // 완전(공백무시) 일치
        if (wn.contains(qNorm) || qNorm.contains(wn)) return 2;   // 포함/시작·끝 일치
        return 1;
    }

    // (3) 유틸: 와일드카드 패턴 (띄어쓰기/하이픈 대비)
    private static String wildcardMiddle(String q) {
        if (q == null) return null;
        String qq = q.trim();
        if (qq.length() <= 1) return qq + "*";
        int mid = qq.length() / 2;
        return qq.substring(0, mid) + "*" + qq.substring(mid);    // 기념*촬영
    }

}