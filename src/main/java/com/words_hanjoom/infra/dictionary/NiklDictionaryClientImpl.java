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
import java.util.*;

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
                                            .defaultIfEmpty(new SenseInfo(exampleFromSearch, null, List.of(), List.of()))
                                            .map(si -> {
                                                String finalExample = !isBlank(si.example()) ? si.example() : exampleFromSearch;
                                                return DictEntry.builder()
                                                        .lemma(lemma)
                                                        .definition(def)
                                                        .fieldType(type)
                                                        .targetCode(targetCode)
                                                        .shoulderNo(supNo)
                                                        .example(finalExample)
                                                        .senseNo(si.senseNo())
                                                        .synonyms(si.synonyms())
                                                        .antonyms(si.antonyms())
                                                        .build();
                                            })
                                            .doOnNext(de -> log.debug("[DICT] merged for {} syn={} ant={} exEmpty={}",
                                                    de.getLemma(),
                                                    de.getSynonyms()==null?0:de.getSynonyms().size(),
                                                    de.getAntonyms()==null?0:de.getAntonyms().size(),
                                                    de.getExample()==null || de.getExample().isBlank(),
                                                    de.getSenseNo()));
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
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .switchIfEmpty(Mono.empty())
                .onErrorResume(e -> {
                    log.warn("[DICT] bodyToMono error(view): {}", e.toString());
                    return Mono.empty();
                })
                .flatMap(body -> {
                    try {
                        if (log.isDebugEnabled()) {
                            String trimmed = body.length() > 2000 ? body.substring(0, 2000) + " …(truncated)" : body;
                            log.debug("[DICT] view raw for {} :: {}", targetCode, trimmed);
                        }

                        JsonNode root = objectMapper.readTree(body);
                        JsonNode item = root.path("channel").path("item");
                        if (item.isMissingNode() || item.isNull()) return Mono.empty();

                        // ▶ JSON 샘플에 맞춰 첫 sense 추출
                        JsonNode sense = pickFirstSense(item);
                        if (sense == null) return Mono.empty();

                        // 예문
                        String example = pickExample(sense);

                        // 의미 번호: 샘플에는 sense_no 대신 sense_code 가 옴(숫자)
                        Short senseNo = null;
                        String senseCodeStr = textOrNull(sense, "sense_code");
                        if (!isBlank(senseCodeStr)) {
                            try { senseNo = Short.valueOf(senseCodeStr); } catch (NumberFormatException ignore) {}
                        }
                        if (senseNo == null) {
                            // 구버전/다른 스키마(있을 수도) 대비
                            senseNo = toShortOrNull(textOrNull(sense, "sense_no"));
                        }

                        // 유의어/반의어
                        List<String> synonyms = collectLexical(item, sense, true);
                        List<String> antonyms = collectLexical(item, sense, false);

                        log.debug("[DICT] view parsed tc={}, syn={}, ant={}, exEmpty={}, senseNo={}",
                                targetCode, synonyms.size(), antonyms.size(), isBlank(example), senseNo);

                        log.info("[DICT] view tc={} → example='{}' syn={} ant={} senseNo={}",
                                targetCode, example, synonyms, antonyms, senseNo);

                        return Mono.just(new SenseInfo(example, senseNo, synonyms, antonyms));
                    } catch (Exception e) {
                        log.warn("[DICT] view parse error: {}", e.toString());
                        return Mono.empty();
                    }
                });
    }

    private static boolean isMissing(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull();
    }
    private static JsonNode first(JsonNode n) {
        if (isMissing(n)) return n;
        return n.isArray() ? n.path(0) : n;
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
        if (isBlank(s)) return null;
        try { return Short.valueOf(s); } catch (Exception e) { return null; }
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
    /** type 문자열이 유의어/반의어 계열인지 판별 */
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


    /** word_info → pos_info[] → comm_pattern_info[] → sense_info[] 첫 번째 sense 노드 선택 */
    private static JsonNode pickFirstSense(JsonNode item) {
        if (item == null || item.isMissingNode() || item.isNull()) return null;
        JsonNode wi = item.path("word_info");
        JsonNode posArr = wi.path("pos_info");
        if (posArr.isArray()) {
            for (JsonNode pos : posArr) {
                JsonNode commArr = pos.path("comm_pattern_info");
                if (commArr.isArray()) {
                    for (JsonNode comm : commArr) {
                        JsonNode senses = comm.path("sense_info");
                        if (senses.isArray() && senses.size() > 0) {
                            return senses.get(0); // 첫 sense
                        } else if (senses.isObject()) {
                            return senses;       // 단일 객체 특수 케이스
                        }
                    }
                }
            }
        }
        // 혹시 상이한 스키마(구버전 등)에 대비해 백업 경로도 시도
        JsonNode direct = item.path("sense_info");
        if (direct.isArray() && direct.size() > 0) return direct.get(0);
        if (direct.isObject()) return direct;
        return null;
    }

    /** sense_info에서 예문 하나 고르기 (우선순위: example_info[].example → 기타 백업 키) */
    private static String pickExample(JsonNode sense) {
        if (sense == null || sense.isMissingNode() || sense.isNull()) return null;

        JsonNode exList = sense.path("example_info");
        if (exList.isArray() && exList.size() > 0) {
            String ex = textOrNull(exList.get(0), "example");
            if (!isBlank(ex)) return ex;
        }
        // 백업 키들
        String ex = textOrNull(sense, "example");
        if (!isBlank(ex)) return ex;
        ex = textOrNull(sense.path("sense_example").path(0), "example");
        if (!isBlank(ex)) return ex;
        ex = textOrNull(sense.path("example_list").path(0), "text");
        return ex;
    }

    /** sense.lexical_info 및 word_info.relation_info/lexical_info 백업까지 긁어오기 */
    private static List<String> collectLexical(JsonNode item, JsonNode sense, boolean wantSyn) {
        LinkedHashSet<String> out = new LinkedHashSet<>();

        java.util.function.Consumer<JsonNode> harvest = container -> {
            if (container == null || container.isMissingNode() || container.isNull()) return;

            JsonNode arr = container.path("lexical_info");
            if (arr.isArray()) {
                for (JsonNode rel : arr) {
                    String type = textOrNull(rel, "type");
                    boolean ok = wantSyn ? isSynonymType(type) : isAntonymType(type);
                    if (!ok) continue;

                    String w = textOrNull(rel, "word");
                    if (isBlank(w)) {
                        JsonNode wn = rel.path("word");
                        if (wn.isObject()) {
                            w = textOrNull(wn, "value");
                            if (isBlank(w)) w = textOrNull(wn, "text");
                        } else if (wn.isArray()) {
                            for (JsonNode el : wn) {
                                String cand = el.isTextual() ? el.asText() : textOrNull(el, "value");
                                if (!isBlank(cand)) out.add(cand);
                            }
                        }
                    }
                    if (!isBlank(w)) out.add(w);
                }
            }

            JsonNode relInfo = container.path("relation_info");
            if (relInfo.isArray()) {
                for (JsonNode rel : relInfo) {
                    String type = textOrNull(rel, "type");
                    boolean ok = wantSyn ? isSynonymType(type) : isAntonymType(type);
                    if (!ok) continue;
                    String w = textOrNull(rel, "word");
                    if (!isBlank(w)) out.add(w);
                }
            }
        };

        harvest.accept(sense);                 // 보통 여기서 대부분 나옴
        harvest.accept(item.path("word_info"));// 백업 경로
        return new ArrayList<>(out);
    }

    record SenseInfo(String example, Short senseNo, List<String> synonyms, List<String> antonyms) {}
}