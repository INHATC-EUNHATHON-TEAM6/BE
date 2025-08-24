package com.words_hanjoom.domain.wordbooks.dto.response;

import com.fasterxml.jackson.annotation.*;
import lombok.Data;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ViewResponse {

    private Channel channel;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Channel {
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<Item> item; // 단건/배열 혼합 대응
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        @JsonProperty("target_code")
        private long targetCode;

        private String word;

        @JsonProperty("sup_no")
        private String supNo;

        private String pos;

        private String definition;

        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<Sense> sense; // 의미 배열
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sense {
        @JsonProperty("sense_no")
        private String senseNo;

        private String definition;
        private String type;      // (예: 일반어 등 – 써도 되고 안 써도 됨)
        private String origin;

        // cat_info: [{ "cat": "심리" }, ...]
        @JsonProperty("cat_info")
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<CatInfo> catInfo;

        // 유/반의어(선택) – DTO에서도 보고 싶을 때
        @JsonProperty("lexical_info")
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<Lexical> lexicalInfo;

        /** 예문은 문자열/객체 혼재 → raw로 받고 편의 getter 제공 */
        @JsonProperty("example")
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<Object> exampleRaw;

        /** 관계어: relation / rel_word 변주 흡수 (선택) */
        @JsonAlias({"relation", "rel_word"})
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<Relation> relation;

        // === 편의 메서드 ===
        public List<String> getExample() {
            if (exampleRaw == null) return Collections.emptyList();
            List<String> out = new ArrayList<>();
            for (Object o : exampleRaw) {
                if (o == null) continue;
                if (o instanceof String s) out.add(s);
                else if (o instanceof Map<?, ?> m) {
                    Object ex = m.get("example");
                    if (ex instanceof String s2) out.add(s2);
                }
            }
            return out;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CatInfo {
        private String cat; // 예: "심리"
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Lexical {
        private String type;  // "유의어"/"반의어"/"비슷한말" 등
        private String word;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Relation {
        private String type; // "유의어"/"반의어" 등
        private String word;
    }
}