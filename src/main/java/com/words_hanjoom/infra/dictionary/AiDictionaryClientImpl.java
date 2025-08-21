// com.words_hanjoom.infra.dictionary.AiDictionaryClientImpl.java
package com.words_hanjoom.infra.dictionary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.words_hanjoom.domain.wordbooks.dto.response.DictEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiDictionaryClientImpl implements AiDictionaryClient {

    @Qualifier("openAiWebClient")
    private final WebClient openAiWebClient;
    private final ObjectMapper om;

    @Value("${openai.model}")
    private String openAiModel;

    @Override
    public Mono<DictEntry> defineFromAi(String surface, String context) {
        // Responses API + Structured Output(JSON Schema)
        Map<String, Object> schema = Map.of(
                "name", "DictEntry",
                "schema", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "required", List.of("lemma", "definition"),
                        "properties", Map.of(
                                "lemma",      Map.of("type","string"),
                                "definition", Map.of("type","string"),
                                "example",    Map.of("type","string"),
                                "synonyms",   Map.of("type","array","items", Map.of("type","string")),
                                "antonyms",   Map.of("type","array","items", Map.of("type","string")),
                                // 카테고리 명 배열(예: ["심리","정보·통신"])
                                "categories", Map.of("type","array","items", Map.of("type","string"))
                        )
                ),
                "strict", true
        );

        String prompt = """
            당신은 한국어 사전 편찬자입니다.
            표제어: %s
            맥락(선택): %s

            표제어의 '정의'를 간결한 한 문장으로 쓰고,
            '예문' 1개, 가능한 경우 '유의어/반의어'를 한국어로 제시하세요.
            'categories'에는 한국 표준 분야명을 사용(예: 심리, 정보·통신). 없으면 비워두세요.
            결과는 반드시 JSON 형식으로만 응답하세요.
            """.formatted(surface, context == null ? "" : context);

        Map<String,Object> body = new HashMap<>();
        body.put("model", openAiModel);
        body.put("input", prompt);
        body.put("response_format", Map.of("type","json_schema","json_schema", schema)); // 구조화 출력 :contentReference[oaicite:1]{index=1}

        return openAiWebClient.post()
                .uri("/responses") // Responses API 엔드포인트 :contentReference[oaicite:2]{index=2}
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(resp -> {
                    try {
                        JsonNode root = om.readTree(resp);
                        // output_text 또는 첫 메시지의 텍스트 꺼내기 (Responses 표준 필드)
                        String json = null;
                        if (root.has("output_text")) {
                            json = root.path("output_text").asText();
                        } else if (root.path("output").isArray()) {
                            JsonNode c = root.path("output").get(0).path("content");
                            if (c.isArray() && c.size()>0) json = c.get(0).path("text").asText(null);
                        }
                        if (json == null || json.isBlank()) {
                            log.warn("[AI] empty output_text: {}", resp);
                            return Mono.empty();
                        }
                        JsonNode j = om.readTree(json);

                        return Mono.just(DictEntry.builder()
                                .lemma(j.path("lemma").asText(surface))
                                .definition(j.path("definition").asText(""))
                                .example(j.path("example").asText(""))
                                .synonyms(readList(j,"synonyms"))
                                .antonyms(readList(j,"antonyms"))
                                .categories(joinCsv(readList(j,"categories"))) // 한글 CSV. 없으면 서비스에서 '일반' 처리
                                .targetCode(0L)   // AI 산출 표식
                                .shoulderNo((byte)0)
                                .senseNo(null)    // 서비스에서 부여
                                .build());
                    } catch (Exception e) {
                        log.warn("[AI] parse error: {}", e.toString());
                        return Mono.empty();
                    }
                });
    }

    private static List<String> readList(JsonNode j, String field) {
        return j.has(field) && j.path(field).isArray()
                ? java.util.stream.StreamSupport.stream(j.path(field).spliterator(), false)
                .map(n -> n.asText())
                .filter(s -> s!=null && !s.isBlank()).distinct().toList()
                : List.of();
    }
    private static String joinCsv(List<String> xs) {
        if (xs==null || xs.isEmpty()) return "";
        return String.join(", ", xs);
    }
}
