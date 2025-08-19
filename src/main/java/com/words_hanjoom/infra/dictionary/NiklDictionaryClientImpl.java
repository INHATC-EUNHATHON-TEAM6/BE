package com.words_hanjoom.infra.dictionary;

import com.words_hanjoom.domain.wordbooks.dto.request.SearchRequest;
import com.words_hanjoom.domain.wordbooks.dto.response.SearchResponse;
import com.words_hanjoom.domain.wordbooks.dto.response.ViewResponse;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Primary
@Slf4j
@Component
@RequiredArgsConstructor
public class NiklDictionaryClientImpl implements NiklDictionaryClient {

    @Qualifier("dicWebClient")
    private final WebClient dicWebClient;

    private final ObjectMapper objectMapper;

    @Value("${nikl.api.key}")
    private String apiKey;

    public static final class QuickEntry {
        public final String lemma;
        public final String targetCode; // 숫자 문자열
        public final String pos;
        public final String definition;
        public QuickEntry(String lemma, String targetCode, String pos, String definition) {
            this.lemma = lemma; this.targetCode = targetCode; this.pos = pos; this.definition = definition;
        }
    }

    /** method(exact/include)로 검색하고 첫 item을 Optional로 반환 */
    private Mono<QuickEntry> searchFirst(String q, String method) {
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
                .flatMap(body -> {
                    try {
                        var root = objectMapper.readTree(body);
                        if (root.at("/channel/total").asInt(0) <= 0) return Mono.empty();
                        var it0   = root.at("/channel/item/0");
                        var lemma = it0.path("word").asText(null);
                        if (lemma == null || lemma.isBlank()) return Mono.empty();
                        var tc    = it0.path("target_code").asText(null);
                        var pos   = it0.path("pos").asText(null);
                        var def   = it0.path("sense").path("definition").asText(null);
                        return Mono.just(new QuickEntry(lemma, tc, pos, def));
                    } catch (Exception e) {
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> Mono.empty()); // ★ 에러도 빈 Mono로
    }

    public Mono<QuickEntry> firstExactOrInclude(String q) {
        return searchFirst(q, "exact")
                .switchIfEmpty(searchFirst(q, "include")); // ★ 폴백
    }

    // =========================
    // Reactive API
    // =========================
    @Override
    public Mono<SearchResponse> search(SearchRequest req) {
        return dicWebClient.get()
                .uri((UriBuilder b) -> {
                    URI u = b.path("/search.do")
                            .queryParam("key", apiKey)
                            .queryParam("q", req.q())
                            .queryParam("req_type", req.reqType())
                            .queryParam("start", req.start())
                            .queryParam("num", req.num())
                            .queryParam("advanced", req.advanced())
                            .queryParamIfPresent("target", req.target())
                            .queryParamIfPresent("method", req.method())
                            .queryParamIfPresent("type1", req.type1())
                            .queryParamIfPresent("type2", req.type2())
                            .queryParamIfPresent("pos", req.pos())
                            .queryParamIfPresent("cat", req.cat())
                            .queryParamIfPresent("multimedia", req.multimedia())
                            .queryParamIfPresent("letter_s", req.letterS())
                            .queryParamIfPresent("letter_e", req.letterE())
                            .queryParamIfPresent("update_s", req.updateS())
                            .queryParamIfPresent("update_e", req.updateE())
                            .build();
                    log.info("[DICT-REQ] {}", u);
                    return u;
                })
                .accept(MediaType.ALL) // ★ 어떤 타입이 와도 받기
                .exchangeToMono(resp -> {
                    log.info("NIKL Content-Type = {}", resp.headers().contentType());
                    return resp.bodyToMono(String.class)
                            .map(body -> read(body, SearchResponse.class));
                });
    }

    @Override
    public Mono<ViewResponse> view(long targetCode) {
        return dicWebClient.get()
                .uri((UriBuilder b) -> {
                    URI u = b.path("/view.do")
                            .queryParam("key", apiKey)
                            .queryParam("target_code", targetCode)
                            .queryParam("req_type", "json")
                            .build();
                    log.info("[DICT-REQ] {}", u);
                    return u;
                })
                .accept(MediaType.ALL) // ★
                .exchangeToMono(resp -> {
                    log.info("[DICT-CT] /view Content-Type={}", resp.headers().contentType());
                    return resp.bodyToMono(String.class)
                            .map(body -> read(body, ViewResponse.class));
                });
    }

    // =========================
    // Facade (blocking)
    // =========================
    @Override
    public Optional<String> findLemma(String surface) {
        try {
            // 1) 정확 일치
            var exactReq = new SearchRequest(
                    surface, "json", 1, 5, "y",
                    Optional.of("exact"),          // ✅ method
                    Optional.empty(),              // target
                    Optional.empty(),              // type1
                    Optional.empty(),              // type2
                    Optional.empty(),              // pos
                    Optional.empty(),              // cat
                    Optional.empty(),              // multimedia
                    Optional.empty(),              // letterS
                    Optional.empty(),              // letterE
                    Optional.empty(),              // updateS
                    Optional.empty()               // updateE
            );

            var srExact = search(exactReq).block(Duration.ofSeconds(5));
            var exactWord = Optional.ofNullable(srExact)
                    .map(SearchResponse::getChannel)
                    .filter(ch -> ch.getTotal() > 0 && ch.getItem() != null && !ch.getItem().isEmpty())
                    .map(ch -> ch.getItem().get(0).getWord());

            if (exactWord.isPresent()) return exactWord;

            // 2) 부분 일치(폴백)
            var includeReq = new SearchRequest(
                    surface, "json", 1, 5, "y",
                    Optional.of("include"),        // ✅ method
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
            );

            var srInclude = search(includeReq).block(Duration.ofSeconds(5));
            return Optional.ofNullable(srInclude)
                    .map(SearchResponse::getChannel)
                    .filter(ch -> ch.getTotal() > 0 && ch.getItem() != null && !ch.getItem().isEmpty())
                    .map(ch -> ch.getItem().get(0).getWord());

        } catch (Exception e) {
            log.warn("[DICT] findLemma EX surface='{}' : {}", surface, e.toString());
            return Optional.empty();
        }
    }

    @Override
    public Optional<Lexeme> lookup(String lemma) {
        SearchResponse sr;
        int items;
        try {
            var exact = new SearchRequest(
                    lemma, "json", 1, 5, "y",
                    Optional.of("exact"),      // 6th: method (String)
                    Optional.empty(),          // 7th: target (Integer)
                    Optional.empty(),          // type1
                    Optional.empty(),          // type2
                    Optional.empty(),          // pos
                    Optional.empty(),          // cat
                    Optional.empty(),          // multimedia
                    Optional.empty(),          // letterS (Integer)
                    Optional.empty(),          // letterE (Integer)
                    Optional.empty(),          // updateS (Integer)
                    Optional.empty()           // updateE (Integer)
            );
            sr = search(exact).block(Duration.ofSeconds(5));
            items = countItems(sr);
            log.info("[DICT-DEBUG] lookup search(exact) items={}", items);

            if (items == 0) {
                var loose = new SearchRequest(
                        lemma, "json", 1, 5, "y",
                        Optional.of("include"),    // method
                        Optional.empty(),          // target
                        Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
                );
                sr = search(loose).block(Duration.ofSeconds(5));
                items = countItems(sr);
                log.info("[DICT-DEBUG] lookup search(loose) items={}", items);
            }
        } catch (Exception e) {
            log.warn("[DICT] lookup EX lemma='{}' : {}", lemma, e.toString());
            return Optional.empty();
        }
        if (items == 0) return Optional.empty();

        // 안전하게 첫 item 꺼내기
        List<SearchResponse.Item> sItems =
                Optional.ofNullable(sr)
                        .map(SearchResponse::getChannel)
                        .map(SearchResponse.Channel::getItem)
                        .orElseGet(Collections::emptyList);

        if (sItems.isEmpty()) {
            return Optional.empty();
        }

        SearchResponse.Item sItem = sItems.get(0);

        // target_code → long 파싱 (비거나 숫자 아님이면 종료)
        Optional<Long> targetCodeOpt = Optional.ofNullable(sItem.getTargetCode())
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .flatMap(s -> {
                    try { return Optional.of(Long.parseLong(s)); }
                    catch (NumberFormatException e) { return Optional.empty(); }
                });

        if (targetCodeOpt.isEmpty()) {
            return Optional.empty();
        }

        final long targetCode = targetCodeOpt.get();
        // search 결과 → target_code / sup_no 추출
        final byte shoulderNo = NiklDictionaryClient.parseByteOrZero(sItem.getSupNo());

        // view 호출
        final ViewResponse vr;
        try {
            vr = view(targetCode).block(Duration.ofSeconds(5));
        } catch (Exception e) {
            return Optional.empty();
        }
        if (vr == null || vr.getChannel() == null || vr.getChannel().getItem() == null || vr.getChannel().getItem().isEmpty()) {
            return Optional.empty();
        }
        var vItem = vr.getChannel().getItem().get(0);

        var senses = vItem.getSense();
        if (senses == null || senses.isEmpty()) return Optional.empty();

        // sense 선택: (SearchResponse에 sense_no 힌트 없으면) 첫 번째 사용
        var sense = pickSense(senses, null);
        if (sense == null) return Optional.empty();

        // sense_no / 정의
        final short senseNo = NiklDictionaryClient.parseShortOrOne(sense.getSenseNo()); // ★ sense_no
        final String definition = safe(sense.getDefinition());

        // 분야(cat)
        List<String> categories = (sense.getCat() == null || sense.getCat().isBlank())
                ? List.of() : List.of(sense.getCat());

        // 예문 (DTO의 편의 getter 사용)
        List<String> examples = sense.getExample();

        // 관계어 → 유의어/반의어 분리
        List<String> synonyms = new ArrayList<>();
        List<String> antonyms = new ArrayList<>();
        splitRelations(sense.getRelation(), synonyms, antonyms);

        // 최종 Lexeme
        var lex = new Lexeme(
                vItem.getWord(),
                synonyms,
                antonyms,
                definition,
                categories,
                examples,
                shoulderNo,     // sup_no (어깨번호)
                targetCode,     // ★ target_code
                senseNo         // ★ sense_no
        );
        return Optional.of(lex);
    }

    // =========================
    // helpers
    // =========================
    private static int countItems(SearchResponse sr) {
        return (sr != null && sr.getChannel() != null && sr.getChannel().getItem() != null)
                ? sr.getChannel().getItem().size()
                : 0;
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    /** sense 힌트가 있으면 그 번호, 없으면 첫 번째 sense 반환 */
    private static ViewResponse.Sense pickSense(List<ViewResponse.Sense> senses, Short senseNoHint) {
        if (senses == null || senses.isEmpty()) return null;
        if (senseNoHint != null && senseNoHint > 0) {
            for (var s : senses) {
                short sn = NiklDictionaryClient.parseShortOrOne(s.getSenseNo());
                if (sn == senseNoHint) return s;
            }
        }
        return senses.get(0);
    }

    /** 관계어에서 유/동의어와 반의어/반대말을 분리 */
    private static void splitRelations(List<ViewResponse.Relation> rels,
                                       List<String> synonyms, List<String> antonyms) {
        if (rels == null || rels.isEmpty()) return;
        for (var r : rels) {
            if (r == null || r.getWord() == null) continue;
            String t = (r.getType() == null) ? "" : r.getType();
            if (t.contains("유의") || t.contains("동의")) {
                synonyms.add(r.getWord());
            } else if (t.contains("반의") || t.contains("반대")) {
                antonyms.add(r.getWord());
            }
        }
    }

    private <T> T read(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (Exception e) {
            log.warn("[DICT] JSON parse fail for {} : {} / raw={}", type.getSimpleName(), e.toString(), body);
            return null;
        }
    }


}
