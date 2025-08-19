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
        String q        = nz(req.q());
        if (q == null) return Mono.empty(); // q 없으면 검색 안 함

        String reqType  = (nz(req.reqType()) != null) ? req.reqType() : "json";
        Integer start   = (req.start() != null) ? req.start() : 1;
        Integer num     = (req.num() != null) ? req.num() : 10;
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
                    qp(ub, "method",     req.method());
                    qp(ub, "target",     req.target());
                    qp(ub, "type1",      req.type1());
                    qp(ub, "type2",      req.type2());
                    qp(ub, "cat",        req.cat());
                    qp(ub, "multimedia", req.multimedia());

                    // --- Optional<Integer> (or Long) ---
                    qp(ub, "letter_s", req.letterS());
                    qp(ub, "letter_e", req.letterE());
                    qp(ub, "update_s",   req.updateS());
                    qp(ub, "update_e",   req.updateE());

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
        return searchFirst(qq, "exact").switchIfEmpty(searchFirst(qq, "include"));
    }

    private Mono<DictEntry> searchFirst(String q, String method) {
        String qq = nz(q);
        if (qq == null) return Mono.empty();          // 공백 단어는 호출하지 않음

        // (선택) 최종 URI 디버그 — 인코딩
        String dbg = org.springframework.web.util.UriComponentsBuilder
                .fromPath("/search.do")
                .queryParam("key", "****" + apiKey.substring(Math.max(0, apiKey.length()-4)))
                .queryParam("q", qq)
                .queryParam("req_type", "json")
                .queryParam("advanced", "y")
                .queryParam("method", method)
                .build()
                .encode(java.nio.charset.StandardCharsets.UTF_8)
                .toUriString();
        log.debug("[DICT] FINAL URI {}", dbg);

        return dicWebClient.get()
                .uri(b -> b.path("/search.do")
                        .queryParam("key", apiKey)       // 검증 통과된 값만
                        .queryParam("q", qq)             // ★ Optional 금지, 공백 금지
                        .queryParam("req_type", "json")
                        .queryParam("advanced", "y")
                        .queryParam("method", method)    // "exact"/"include"
                        .build())
                .accept(MediaType.parseMediaType("text/json"))
                .retrieve()
                .onStatus(s -> s.isError(), resp ->
                        resp.bodyToMono(String.class).defaultIfEmpty("")
                                .flatMap(bd -> {
                                    log.error("[DICT] searchFirst({}) HTTP {} body={}", method, resp.statusCode(), bd);
                                    return Mono.error(new IllegalStateException("NIKL searchFirst error: " + resp.statusCode()));
                                }))
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(7))
                .flatMap(body -> { /* 기존 파싱 로직 그대로 */
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        JsonNode itemArray = root.at("/channel/item");
                        if (itemArray.isMissingNode() || !itemArray.isArray()) return Mono.empty();

                        return Flux.fromIterable(itemArray)
                                .filter(itemNode -> qq.equals(textOrNull(itemNode, "word")))
                                .next()
                                .flatMap(itemNode -> {
                                    JsonNode senseNode = itemNode.path("sense");
                                    if (senseNode.isMissingNode() || senseNode.isNull()) return Mono.empty();

                                    String lemma = textOrNull(itemNode, "word");
                                    String def   = textOrNull(senseNode, "definition");
                                    String type  = textOrNull(senseNode, "type");
                                    byte supNo   = (byte) itemNode.path("sup_no").asInt(0);
                                    long tc      = itemNode.path("target_code").asLong(0);

                                    return fetchOneExample(tc)
                                            .onErrorResume(e -> Mono.empty())
                                            .map(ex -> new DictEntry(lemma, def, type, supNo, ex))
                                            .switchIfEmpty(Mono.fromSupplier(() -> new DictEntry(lemma, def, type, supNo, null)));
                                });
                    } catch (Exception e) {
                        log.warn("[DICT] parse error(searchFirst {}): {}", method, e.toString(), e);
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.warn("[DICT] searchFirst({}) pipeline error: {}", method, e.toString(), e);
                    return Mono.empty();
                });
    }


    private Mono<String> fetchOneExample(long targetCode) {
        if (targetCode <= 0) return Mono.empty();

        return dicWebClient.get()
                .uri(b -> b.path("/view.do")
                        .queryParam("key", apiKey)
                        .queryParam("req_type", "json")
                        .queryParam("target_code", targetCode)
                        .build())
                .accept(MediaType.parseMediaType("text/json"))
                .retrieve()
                .bodyToMono(String.class)
                .switchIfEmpty(Mono.empty()) // ✅ 수정: 빈 응답일 경우 NullPointerException 방지
                .onErrorResume(e -> {
                    log.warn("[DICT] bodyToMono error(view): {}", e.toString());
                    return Mono.empty();
                })
                .flatMap(body -> {
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        JsonNode itemNode = root.at("/channel/item/0");

                        // ✅ 수정: item 노드가 유효하지 않으면 Mono.empty() 반환
                        if (itemNode.isMissingNode() || itemNode.isNull()) {
                            return Mono.empty();
                        }

                        JsonNode senseNode = itemNode.path("sense");
                        if (senseNode.isMissingNode() || senseNode.isNull()) {
                            return Mono.empty();
                        }

                        String ex = textOrNull(senseNode, "example");
                        if (isBlank(ex)) ex = textOrNull(senseNode.path("examples").path(0), "example");
                        if (isBlank(ex)) ex = textOrNull(senseNode.path("example_list").path(0), "text");
                        if (isBlank(ex)) ex = textOrNull(senseNode.path("sense_example").path(0), "example");

                        return isBlank(ex) ? Mono.empty() : Mono.just(ex);
                    } catch (Exception e) {
                        log.warn("[DICT] example parse error: {}", e.toString());
                        return Mono.empty();
                    }
                });
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
}