// com.words_hanjoom.infra.dictionary.AiDictionaryClientImpl.java
package com.words_hanjoom.infra.dictionary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.words_hanjoom.domain.wordbooks.dto.response.DictEntry;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.jayway.jsonpath.JsonPath;
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

    @Override
    public Mono<DictEntry> defineFromAi(String surface, @Nullable String context) {

        String prompt = """
        당신은 한국어 사전 편찬자입니다.
        표제어: %s
        맥락(선택): %s
        
        표제어의 '정의'를 간결한 한 문장으로 쓰고,
        '예문' 1개, 가능한 경우 '유의어'와 '반의어'를 한국어로 제시하세요.
        'categories'에는 한국 표준 분야명을 사용(예: 심리, 정보·통신). 없으면 비워두세요.
        응답은 아래와 같은 JSON 객체 형식으로만 제공해야 합니다.
        {
            "lemma": "...",
            "definition": "...",
            "example": "...",
            "synonyms": ["..."],
            "antonyms": ["..."],
            "categories": ["..."]
        }
        """.formatted(surface, context == null ? "" : context);

        Map<String, Object> body = new HashMap<>();
        body.put("model", openAiModel);
        body.put("response_format", Map.of("type", "json_object")); // JSON 모드 활성화
        body.put("messages", List.of(
                Map.of("role", "system", "content", "You are a helpful assistant that responds in JSON format."),
                Map.of("role", "user", "content", prompt)
        ));

        return openAiWebClient.post()
                .uri("/chat/completions") // 엔드포인트 변경
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(),
                        resp -> resp.bodyToMono(String.class)
                                .doOnNext(b -> log.warn("[AI] HTTP {} {}", resp.statusCode(), b))
                                .then(Mono.error(new IllegalStateException("OpenAI error"))))
                .bodyToMono(String.class)
                .doOnNext(json -> log.info("[AI] Received JSON: {}", json))
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(e -> { log.warn("[AI] error: {}", e.toString()); return Mono.empty(); })
                .flatMap(json -> {
                    String jsonContent = com.jayway.jsonpath.JsonPath.read(json, "$.choices[0].message.content");
                    if (jsonContent == null || jsonContent.isBlank()) return Mono.empty();

                    ObjectMapper om = new ObjectMapper();
                    try {
                        JsonNode node = om.readTree(jsonContent); // 직접 JSON 파싱
                        String lemma      = optText(node, "lemma", null);
                        String definition = optText(node, "definition", null);
                        String example    = optText(node, "example", null);
                        List<String> synonyms = toStrList(node.get("synonyms"));
                        List<String> antonyms = toStrList(node.get("antonyms"));
                        List<String> catsList = toStrList(node.get("categories"));

                        if (lemma == null || definition == null) return Mono.empty();

                        DictEntry entry = DictEntry.builder()
                                .lemma(lemma)
                                .definition(definition)
                                .example(example)
                                .synonyms(synonyms)
                                .antonyms(antonyms)
                                .categories(String.join(",", catsList))
                                .targetCode(0L)
                                .senseNo(null)
                                .shoulderNo((byte)0)
                                .build();
                        return Mono.just(entry);
                    } catch (Exception e) {
                        log.warn("[AI] parse error: {}", e.toString());
                        return Mono.empty();
                    }
                });
    }

    private static String optText(JsonNode n, String field, String defVal) {
        JsonNode v = (n == null) ? null : n.get(field);
        return (v != null && v.isTextual()) ? v.asText() : defVal;
    }

    private static List<String> toStrList(JsonNode arr) {
        if (arr == null || !arr.isArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonNode e : arr) if (e != null && e.isTextual()) out.add(e.asText());
        return out;
    }
}
