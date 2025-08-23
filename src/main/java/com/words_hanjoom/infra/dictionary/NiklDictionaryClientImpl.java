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
    public Mono<List<DictEntry>> lookupAllSenses(String surface) {
        return dicWebClient.get()
                .uri(b -> b.path("/search.do")
                        .queryParam("q", surface)
                        .queryParam("key", apiKey)
                        .queryParam("req_type", "json")
                        .queryParam("advanced", "y")
                        .queryParam("method", "exact")
                        .queryParam("num", 50)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(j -> log.info("[NIKL] raw: {}", j))     // ★ 원문 확인
                .map(this::parseToEntries)
                .onErrorResume(e -> {
                    log.warn("[NIKL] lookupAllSenses error: {}", e.toString());
                    return Mono.just(Collections.emptyList());
                })
                .defaultIfEmpty(Collections.emptyList());
    }



    private List<DictEntry> parseToEntries(String json) {
        List<DictEntry> out = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);

            // 1) item 리스트를 어디서든 찾아오기 (channel.item | items | item)
            List<JsonNode> itemList = extractItems(root);
            if (itemList.isEmpty()) return out;

            for (JsonNode item : itemList) {
                String lemma   = pickText(item, "word", "headword", "term"); // 표제어
                long targetCd  = pickLong(item, 0L, "target_code", "targetCode");
                int supNo      = (int) pickLong(item, 0L, "sup_no", "supNo");

                // 2) sense 배열/단일 모두 처리
                List<JsonNode> senses = extractSenses(item);

                // 3) sense가 없고 item에 definition이 직접 있을 때(일부 응답)
                if (senses.isEmpty()) {
                    String def = pickText(item, "definition", "desc", "sense_def");
                    if (!lemma.isBlank() || !def.isBlank()) {
                        out.add(buildEntry(lemma, def, "", null, "", targetCd, supNo));
                    }
                    continue;
                }

                // 4) sense들 파싱
                for (JsonNode s : senses) {
                    String def   = pickText(s, "definition", "def", "sense_definition");
                    String ex    = pickExample(s);
                    Integer sn   = pickIntObj(s, "sense_no", "senseNo");
                    String cats  = collectCategories(s); // cat/subject/domain/field...

                    if (lemma.isBlank() && def.isBlank()) continue;
                    out.add(buildEntry(lemma, def, ex, sn, cats, targetCd, supNo));
                }
            }
        } catch (Exception e) {
            log.warn("[NIKL] parse error: {}", e.toString());
        }
        return out;
    }
    private List<JsonNode> extractItems(JsonNode root) {
        List<JsonNode> items = new ArrayList<>();

        // A) channel.item (표준국어대사전/우리말샘 전형)
        JsonNode channel = root.path("channel");
        JsonNode chItems = channel.path("item");
        if (chItems.isArray()) chItems.forEach(items::add);
        else if (!chItems.isMissingNode() && !chItems.isNull()) items.add(chItems);

        // B) items (신형 계열)
        if (items.isEmpty()) {
            JsonNode i1 = root.path("items");
            if (i1.isArray()) i1.forEach(items::add);
            else if (!i1.isMissingNode() && !i1.isNull()) {
                // items가 객체이고 그 안에 item[]인 경우
                JsonNode nested = i1.path("item");
                if (nested.isArray()) nested.forEach(items::add);
                else if (!nested.isMissingNode() && !nested.isNull()) items.add(nested);
                else items.add(i1); // 그냥 item처럼
            }
        }

        // C) 최상위 item[]
        if (items.isEmpty()) {
            JsonNode itemTop = root.path("item");
            if (itemTop.isArray()) itemTop.forEach(items::add);
            else if (!itemTop.isMissingNode() && !itemTop.isNull()) items.add(itemTop);
        }
        return items;
    }

    private DictEntry buildEntry(String lemma, String def, String ex, Integer sn, String cats, long targetCd, int supNo) {
        return DictEntry.builder()
                .lemma(nz(lemma))
                .definition(nz(def))
                .example(nz(ex))
                .categories(nz(cats))
                .targetCode(targetCd)
                .senseNo(sn)
                .shoulderNo((byte) Math.max(0, Math.min(127, supNo)))
                .synonyms(List.of())
                .antonyms(List.of())
                .build();
    }

    private List<JsonNode> extractSenses(JsonNode item) {
        List<JsonNode> senses = new ArrayList<>();
        JsonNode s = item.path("sense");
        if (s.isArray()) s.forEach(senses::add);
        else if (!s.isMissingNode() && !s.isNull()) senses.add(s);

        // 일부 응답: sense_info 또는 senses 등 다른 키
        if (senses.isEmpty()) {
            for (String alt : List.of("senses", "sense_info", "senseInfo")) {
                JsonNode altNode = item.path(alt);
                if (altNode.isArray()) altNode.forEach(senses::add);
                else if (!altNode.isMissingNode() && !altNode.isNull()) senses.add(altNode);
            }
        }
        return senses;
    }

    private Integer pickIntObj(JsonNode n, String... keys) {
        if (n == null) return null;
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v != null && v.isNumber()) return v.asInt();
            if (v != null && v.isTextual()) try { return Integer.parseInt(v.asText()); } catch (Exception ignore) {}
        }
        return null;
    }

    private String pickText(JsonNode n, String... keys) {
        if (n == null) return "";
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v != null && v.isTextual()) return v.asText().trim();
        }
        return "";
    }

    private long pickLong(JsonNode n, long def, String... keys) {
        if (n == null) return def;
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v != null && v.isNumber()) return v.asLong();
            if (v != null && v.isTextual()) try { return Long.parseLong(v.asText()); } catch (Exception ignore) {}
        }
        return def;
    }

    private static String getText(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v != null && v.isTextual()) ? v.asText().trim() : "";
    }
    private static long getLong(JsonNode n, String field, long def) {
        JsonNode v = n.get(field);
        return (v != null && v.isNumber()) ? v.asLong() : def;
    }
    private static int getInt(JsonNode n, String field, int def) {
        JsonNode v = n.get(field);
        return (v != null && v.isNumber()) ? v.asInt() : def;
    }
    private static Integer getIntObj(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v != null && v.isNumber()) ? v.asInt() : null;
    }

    private static String getExample(JsonNode sense) {
        // 흔한 케이스 1) "example": "문장..."
        JsonNode ex = sense.get("example");
        if (ex != null && ex.isTextual()) return ex.asText();

        // 케이스 2) "example"가 배열/객체(예: [{"type":"용례","text":"..."}])
        if (ex != null && ex.isArray() && ex.size() > 0) {
            JsonNode first = ex.get(0);
            if (first.isTextual()) return first.asText();
            JsonNode text = first.get("text");
            if (text != null && text.isTextual()) return text.asText();
        }
        return "";
    }

    private static String collectCategories(JsonNode sense) {
        // 곳에 따라 cat/subject/domain/field 등으로 들어올 수 있음. 있는 것만 취합.
        List<String> cats = new ArrayList<>();
        for (String key : List.of("cat", "subject", "domain", "field", "classification")) {
            String v = getText(sense, key);
            if (!v.isBlank()) cats.add(v);
        }
        // 중복 제거 + CSV
        return String.join(",", new LinkedHashSet<>(cats));
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
        if (targetCode <= 0) return Mono.empty();

        Mono<String> primary = dicWebClient.get()
                .uri(b -> b.path("/view.do")
                        .queryParam("key", apiKey)
                        .queryParam("req_type", "json")
                        .queryParam("method", "target_code")     // 공식 라우트
                        .queryParam("target_code", targetCode)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class);

        Mono<String> fallback = dicWebClient.get()
                .uri(b -> b.path("/view.do")
                        .queryParam("key", apiKey)
                        .queryParam("req_type", "json")
                        .queryParam("type_search", "view")       // 폴백 라우트
                        .queryParam("method", "TARGET_CODE")
                        .queryParam("q", targetCode)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class);

        return primary
                .onErrorResume(e -> fallback)                // HTTP 에러면 폴백
                .flatMap(body -> {
                    try {
                        ViewResponse vr = read(body, ViewResponse.class);
                        // channel.item이 비거나 누락되어 있으면 폴백으로 한 번 더
                        boolean hasItem = vr != null
                                && vr.getChannel() != null
                                && vr.getChannel().getItem() != null
                                && !vr.getChannel().getItem().isEmpty();
                        if (hasItem) return Mono.just(vr);
                    } catch (Exception ignore) {
                        // 파싱 실패 시 폴백으로
                    }
                    return fallback.map(b -> read(b, ViewResponse.class));
                });
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
                        int bodyLen = (body == null) ? -1 : body.length();
                        JsonNode root = objectMapper.readTree(body);
                        JsonNode itemsNode = root.path("channel").path("item");

                        // ✅ 단건/배열 모두 수용
                        List<JsonNode> items = asList(itemsNode);

                        String itemKind = itemsNode.isArray() ? "array(" + items.size() + ")"
                                : itemsNode.isObject() ? "object"
                                : itemsNode.isMissingNode() ? "missing"
                                : itemsNode.isNull() ? "null"
                                : "other(" + itemsNode.getNodeType() + ")";
                        log.debug("[DICT][searchFirst {}:{}] bodyLen={}, itemKind={}", method, target, bodyLen, itemKind);

                        if (items.isEmpty()) {
                            log.debug("[DICT] items empty → return empty");
                            return Mono.empty();
                        }

                        // 가장 적합한 item 고르기 (완전일치 우선, 아니면 첫번째)
                        JsonNode item = null;
                        for (JsonNode it : items) {
                            if (qq.equals(textOrNull(it, "word"))) { item = it; break; }
                        }
                        if (item == null) item = items.get(0);

                        String lemma = textOrNull(item, "word");
                        long tc = item.path("target_code").asLong(0);
                        if (tc <= 0) return Mono.empty();

                        JsonNode senseFromSearch = pickFirstSense(item);
                        String def = textOrNull(senseFromSearch, "definition");
                        String exampleFromSearch = pickExample(senseFromSearch);
                        byte   supNo = (byte) item.path("sup_no").asInt(0);

                        // ✅ search 응답에서도 syn/ant/cat 뽑아두기
                        List<String>  synFromSearch = collectLexical(item, senseFromSearch, true);
                        List<String>  antFromSearch = collectLexical(item, senseFromSearch, false);
                        List<String> catsFromSearch = collectCatNames(item, senseFromSearch);

                        log.debug("[DICT] search hit lemma='{}' tc={} → call view.do", lemma, tc);

                        // --- view.do 호출 ---
                        return fetchFirstSenseInfo(tc)
                                .defaultIfEmpty(new SenseInfo(null, null, List.of(), List.of(), List.of()))
                                .map(si -> {
                                    String finalExample = !isBlank(si.example()) ? si.example() : exampleFromSearch;

                                    LinkedHashSet<String> syn = new LinkedHashSet<>(si.synonyms());
                                    syn.addAll(synFromSearch);

                                    LinkedHashSet<String> ant = new LinkedHashSet<>(si.antonyms());
                                    ant.addAll(antFromSearch);

                                    // ✅ 카테고리: view + search 병합 후 CSV
                                    LinkedHashSet<String> cats = new LinkedHashSet<>(si.catNames());
                                    cats.addAll(catsFromSearch);
                                    String categories = toCsvStr(cats);
                                    if (categories.isBlank()) categories = "일반";

                                    return DictEntry.builder()
                                            .lemma(lemma)
                                            .definition(def)
                                            .targetCode(tc)
                                            .shoulderNo(supNo)
                                            .example(finalExample)
                                            .senseNo(si.senseNo())
                                            .categories(categories)       // ← 오직 catCsv만!
                                            .synonyms(new ArrayList<>(syn))
                                            .antonyms(new ArrayList<>(ant))
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

        Mono<String> primary = dicWebClient.get()
                .uri(b -> b.path("/view.do")
                        .queryParam("key", apiKey)
                        .queryParam("req_type", "json")
                        .queryParam("method", "target_code")      // 공식 라우트
                        .queryParam("target_code", targetCode)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class);

        Mono<String> fallback = dicWebClient.get()
                .uri(b -> b.path("/view.do")
                        .queryParam("key", apiKey)
                        .queryParam("req_type", "json")
                        .queryParam("type_search", "view")        // 폴백 라우트
                        .queryParam("method", "TARGET_CODE")
                        .queryParam("q", targetCode)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class);

        // 1차: primary, 실패/빈결과면 2차: fallback
        return primary
                .onErrorResume(e -> fallback)
                .flatMap(body -> parseViewBody(body, targetCode))
                .switchIfEmpty(fallback.flatMap(body -> parseViewBody(body, targetCode)));
    }

    private Mono<SenseInfo> parseViewBody(String body, long targetCode) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode items = root.path("channel").path("item");
            if (isMissing(items)) return Mono.empty();

            // ✅ 1) 배열이면 target_code로 정확히 매칭, 없으면 첫 번째
            JsonNode chosen = items.isArray() ? findByTargetCode(items, targetCode) : items;
            if (isMissing(chosen)) return Mono.empty();

            // ✅ 2) sense 선택 (sense 또는 pickFirstSense 모두 지원)
            JsonNode senseNode = firstSenseNode(chosen);
            if (isMissing(senseNode)) return Mono.empty();

            // ✅ 3) 상세정보 추출
            String example = pickExampleFlexible(senseNode);
            Integer senseNo = firstSenseCode(senseNode);
            if (senseNo == null) senseNo = firstSenseCodeInItem(chosen);

            List<String> synonyms = collectLexical(chosen, senseNode, true);
            List<String> antonyms = collectLexical(chosen, senseNode, false);
            List<String> catNames = collectCatNames(chosen, senseNode);

            return Mono.just(new SenseInfo(example, senseNo, synonyms, antonyms, catNames));
        } catch (Exception e) {
            log.warn("[DICT][view] parse error tc={}: {}", targetCode, e.toString());
            return Mono.empty();
        }
    }

    @Override
    public Mono<DictEntry> enrichFromView(DictEntry base) {
        long tc = Optional.ofNullable(base.getTargetCode()).orElse(0L);
        if (tc <= 0) return Mono.just(base);

        return fetchFirstSenseInfo(tc)   // 기존 private 메서드
                .defaultIfEmpty(new SenseInfo(null, null, List.of(), List.of(), List.of()))
                .map(si -> {
                    String example = (base.getExample() != null && !base.getExample().isBlank())
                            ? base.getExample() : nz(si.example());

                    Integer senseNo = (base.getSenseNo() != null && base.getSenseNo() != 0)
                            ? base.getSenseNo() : si.senseNo();

                    LinkedHashSet<String> syn = new LinkedHashSet<>(base.getSynonyms() == null ? List.of() : base.getSynonyms());
                    syn.addAll(si.synonyms());

                    LinkedHashSet<String> ant = new LinkedHashSet<>(base.getAntonyms() == null ? List.of() : base.getAntonyms());
                    ant.addAll(si.antonyms());

                    LinkedHashSet<String> cats = new LinkedHashSet<>();
                    if (base.getCategories() != null && !base.getCategories().isBlank()) {
                        for (String t : base.getCategories().split("\\s*,\\s*")) {
                            if (!t.isBlank()) cats.add(t.trim());
                        }
                    }
                    cats.addAll(si.catNames());
                    String categories = toCsvStr(cats);
                    if (categories.isBlank()) categories = "일반";

                    return DictEntry.builder()
                            .lemma(base.getLemma())
                            .definition(base.getDefinition())
                            .example(example)
                            .synonyms(new ArrayList<>(syn))
                            .antonyms(new ArrayList<>(ant))
                            .categories(categories)
                            .targetCode(base.getTargetCode())
                            .senseNo(senseNo)
                            .shoulderNo(base.getShoulderNo())
                            .build();
                });
    }

    private static JsonNode findByTargetCode(JsonNode arr, long tc) {
        if (arr == null || !arr.isArray() || arr.size() == 0) return MissingNode.getInstance();
        for (JsonNode n : arr) {
            long t = n.path("target_code").isTextual()
                    ? Long.parseLong(n.path("target_code").asText("0"))
                    : n.path("target_code").asLong(0);
            if (t == tc) return n;
        }
        return arr.get(0);
    }

    private static JsonNode firstSenseNode(JsonNode item) {
        JsonNode s = item.path("sense");
        if (s.isArray()) return s.size() > 0 ? s.get(0) : MissingNode.getInstance();
        if (!isMissing(s)) return s;
        return pickFirstSense(item); // 기존 휴리스틱
    }

    private static String pickExampleFlexible(JsonNode sense) {
        if (isMissing(sense)) return null;
        // case 1) "example": "..."
        JsonNode ex = sense.path("example");
        if (ex.isTextual()) return ex.asText();

        // case 2) "example": [ "..." | {"example":"..."} | {"text":"..."} ]
        if (ex.isArray()) {
            for (JsonNode e : ex) {
                if (e.isTextual()) return e.asText();
                String v = e.path("example").asText(null);
                if (v != null && !v.isBlank()) return v;
                v = e.path("text").asText(null);
                if (v != null && !v.isBlank()) return v;
            }
        }

        // case 3) "example_info": [ {"example":"..."} | {"text":"..."} ]
        JsonNode exInfo = sense.path("example_info");
        if (exInfo.isArray() && exInfo.size() > 0) {
            JsonNode e = exInfo.get(0);
            String v = e.path("example").asText(null);
            if (v != null && !v.isBlank()) return v;
            v = e.path("text").asText(null);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
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

    private static Integer toIntOrNull(String s) {
        if (isBlank(s)) return null;
        try { return Integer.valueOf(s.trim()); } catch (Exception e) { return null; }
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
                    JsonNode alt = item.path("sense_info");
                    if (alt.isArray() && alt.size() > 0) return alt.get(0);  // ★ 추가
                    return senses.get(0);
                } else if (senses.isObject()) {
                    JsonNode alt = item.path("sense_info");
                    if (alt.isArray() && alt.size() > 0) return alt.get(0);  // ★ 추가
                    return senses;
                }
            }
        }
        JsonNode alt = item.path("sense_info");
        if (alt.isArray() && alt.size() > 0) return alt.get(0);  // ★ 추가
        return item.path("sense");
    }

    private static String pickExample(JsonNode sense) {
        if (sense == null || sense.isMissingNode() || sense.isNull()) return null;
        for (JsonNode exNode : asList(sense.path("example_info"))) {
            String ex = textOrNull(exNode, "example");
            if (ex != null && !ex.isBlank()) return ex;
        }
        return textOrNull(sense, "example");
    }

    private static List<JsonNode> asList(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return List.of();
        if (n.isArray()) { List<JsonNode> r = new ArrayList<>(); n.forEach(r::add); return r; }
        return List.of(n);
    }

    private static List<String> collectLexical(JsonNode item, JsonNode sense, boolean wantSyn) {
        Set<String> out = new LinkedHashSet<>();

        // 1) sense.lexical_info
        for (JsonNode node : asList(sense.path("lexical_info"))) {
            String type = textOrNull(node, "type");
            if ((wantSyn && isSynonymType(type)) || (!wantSyn && isAntonymType(type))) {
                String w = textOrNull(node, "word");
                if (w != null && !w.isBlank()) out.add(w);
            }
        }

        // 2) item.word_info.relation_info (기존)
        for (JsonNode node : asList(item.path("word_info").path("relation_info"))) {
            String type = textOrNull(node, "type");
            if ((wantSyn && isSynonymType(type)) || (!wantSyn && isAntonymType(type))) {
                String w = textOrNull(node, "word");
                if (w != null && !w.isBlank()) out.add(w);
            }
        }

        // 3) 표준국어대사전 변주: item.relation / item.rel_word
        for (JsonNode node : asList(item.path("relation"))) {
            String type = textOrNull(node, "type");
            if ((wantSyn && isSynonymType(type)) || (!wantSyn && isAntonymType(type))) {
                String w = textOrNull(node, "word");
                if (w != null && !w.isBlank()) out.add(w);
            }
        }

        for (JsonNode node : asList(item.path("rel_word"))) {
            String type = textOrNull(node, "type");
            if ((wantSyn && isSynonymType(type)) || (!wantSyn && isAntonymType(type))) {
                String w = textOrNull(node, "word");
                if (w != null && !w.isBlank()) out.add(w);
            }
        }

        return new ArrayList<>(out);
    }

    private static Integer firstSenseCode(JsonNode sense) {
        if (isMissing(sense)) return null;
        // 선호 순서: sense_code → sense_no (응답 변주 대비)
        Integer v = toIntOrNull(sense.path("sense_code").asText(null));
        if (v != null) return v;
        return toIntOrNull(sense.path("sense_no").asText(null));
    }
    private static Integer firstSenseCodeInItem(JsonNode item) {
        for (JsonNode pos : asList(item.path("word_info").path("pos_info"))) {
            for (JsonNode comm : asList(pos.path("comm_pattern_info"))) {
                for (JsonNode s : asList(comm.path("sense_info"))) {
                    Integer v = firstSenseCode(s);
                    if (v != null) return v;
                }
            }
        }
        for (JsonNode s : asList(item.path("sense_info"))) {
            Integer v = firstSenseCode(s);
            if (v != null) return v;
        }
        return null;
    }

    // cat_info → 코드 리스트 수집 (선택 sense 우선, 없으면 같은 item의 모든 sense 훑기)
    private static List<String> collectCatNames(JsonNode item, JsonNode sense) {
        LinkedHashSet<String> out = new LinkedHashSet<>();

        for (JsonNode c : asList(sense.path("cat_info"))) {
            String cat = textOrNull(c, "cat");
            if (!isBlank(cat)) out.add(cat);
        }
        if (out.isEmpty()) {
            for (JsonNode pos : asList(item.path("word_info").path("pos_info"))) {
                for (JsonNode comm : asList(pos.path("comm_pattern_info"))) {
                    for (JsonNode s : asList(comm.path("sense_info"))) {
                        for (JsonNode c : asList(s.path("cat_info"))) {
                            String cat = textOrNull(c, "cat");
                            if (!isBlank(cat)) out.add(cat);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }

    private static String toCsvStr(Collection<String> xs) {
        if (xs == null || xs.isEmpty()) return "";
        return xs.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    record SenseInfo(String example, Integer senseNo, List<String> synonyms,
                     List<String> antonyms, List<String> catNames) {}
}
