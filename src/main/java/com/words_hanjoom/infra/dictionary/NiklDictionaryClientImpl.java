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
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NiklDictionaryClientImpl implements NiklDictionaryClient {

    @Qualifier("dicWebClient")
    private final WebClient dicWebClient;         // baseUrl = https://stdict.korean.go.kr/api
    private final ObjectMapper objectMapper;

    @Value("${nikl.api.key}")
    private String apiKey;

    // ---------- 필수 구현 1: search ----------
    @Override
    public Mono<SearchResponse> search(SearchRequest req) {
        return dicWebClient.get()
                .uri(b -> b.path("/search.do")
                        .queryParam("key", apiKey)
                        .queryParam("q", req.q())
                        .queryParam("req_type", req.reqType())
                        .queryParam("start", req.start())
                        .queryParam("num", req.num())
                        .queryParam("advanced", req.advanced())
                        .queryParamIfPresent("target",    req.target())
                        .queryParamIfPresent("method",    req.method())
                        .queryParamIfPresent("type1",     req.type1())
                        .queryParamIfPresent("type2",     req.type2())
                        .queryParamIfPresent("pos",       req.pos())
                        .queryParamIfPresent("cat",       req.cat())
                        .queryParamIfPresent("multimedia",req.multimedia())
                        .queryParamIfPresent("letter_s",  req.letterS())
                        .queryParamIfPresent("letter_e",  req.letterE())
                        .queryParamIfPresent("update_s",  req.updateS())
                        .queryParamIfPresent("update_e",  req.updateE())
                        .build())
                .accept(MediaType.ALL)
                .exchangeToMono(resp -> resp.bodyToMono(String.class)
                        .map(body -> read(body, SearchResponse.class)));
    }

    // ---------- 필수 구현 2: view ----------
    @Override
    public Mono<ViewResponse> view(long targetCode) {
        return dicWebClient.get()
                .uri(b -> b.path("/view.do")
                        .queryParam("key", apiKey)
                        .queryParam("req_type", "json")
                        .queryParam("target_code", targetCode)
                        .build())
                .accept(MediaType.ALL)
                .exchangeToMono(resp -> resp.bodyToMono(String.class)
                        .map(body -> read(body, ViewResponse.class)));
    }

    // ---------- 필수 구현 3: quickLookup ----------
    @Override
    public Mono<DictEntry> quickLookup(String q) {
        return searchFirst(q, "exact").switchIfEmpty(searchFirst(q, "include"));
    }

    private Mono<DictEntry> searchFirst(String q, String method) {
        return dicWebClient.get()
                .uri(b -> b.path("/search.do")
                        .queryParam("key", apiKey)
                        .queryParam("q", q)
                        .queryParam("req_type", "json")
                        .queryParam("advanced", "y")
                        .queryParam("method", method)
                        .build())
                .accept(MediaType.ALL)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(7))
                .flatMap(body -> {
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        if (root.at("/channel/total").asInt(0) <= 0) return Mono.empty();

                        JsonNode it0 = root.at("/channel/item/0");
                        String lemma = textOrNull(it0, "word");
                        String def   = textOrNull(it0.path("sense"), "definition");
                        String type  = textOrNull(it0.path("sense"), "type");
                        byte   supNo = (byte) it0.path("sup_no").asInt(0);
                        long   tc    = it0.path("target_code").asLong(0);
                        if (lemma == null || lemma.isBlank()) return Mono.empty();

                        return fetchOneExample(tc)
                                .onErrorResume(e -> Mono.empty())
                                .defaultIfEmpty(null)
                                .map(ex -> new DictEntry(lemma, def, type, supNo, ex));
                    } catch (Exception e) {
                        log.warn("[DICT] parse error(searchFirst {}): {}", method, e.toString());
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.warn("[DICT] HTTP error(searchFirst {}): {}", method, e.toString());
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
                .accept(MediaType.ALL)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(body -> {
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        JsonNode sense = root.at("/channel/item/0/sense");
                        String ex = textOrNull(sense, "example");
                        if (isBlank(ex)) ex = textOrNull(sense.path("examples").path(0), "example");
                        if (isBlank(ex)) ex = textOrNull(sense.path("example_list").path(0), "text");
                        if (isBlank(ex)) ex = textOrNull(sense.path("sense_example").path(0), "example");
                        return isBlank(ex) ? Mono.empty() : Mono.just(ex);
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                });
    }

    private <T> T read(String body, Class<T> type) {
        try { return objectMapper.readValue(body, type); }
        catch (Exception e) { throw new RuntimeException("DICT_PARSE_ERROR: " + e.getMessage(), e); }
    }
    private static String textOrNull(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) return null;
        JsonNode n = node.path(field);
        if (n.isMissingNode()) return null;
        String s = n.asText(null);
        return isBlank(s) ? null : s;
    }
    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
