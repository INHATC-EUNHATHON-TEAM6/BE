package com.words_hanjoom.domain.wordbooks.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * /view.do 응답 DTO (상세)
 * - 한 target_code 에 여러 의미(sense)가 포함될 수 있음
 * - 예문은 문자열 배열 또는 객체 배열( { "example": "..." } ) 형태 모두 수용
 * - 관계어 필드는 relation / rel_word 등 변주를 흡수
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ViewResponse {

    private Channel channel;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Channel {
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<Item> item; // API 특성상 단건/배열 혼합 케이스 존재
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

        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<Sense> sense; // 의미 배열
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sense {
        @JsonProperty("sense_no")
        private String senseNo;

        private String definition;
        private String type;      // (예: 일반어 등)
        private String origin;    // 원어
        private String cat;       // 전문 분야

        /**
         * 예문은 "문자열" 또는 {"example":"문자열"} 객체로 내려올 수 있으므로
         * raw로 받고, 편의 getter(getExample)를 통해 List<String> 형태로 노출한다.
         */
        @JsonProperty("example")
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<Object> exampleRaw;

        /**
         * 관계어: 필드명이 relation 또는 rel_word 등으로 오므로 alias 처리.
         * 원소는 { "type": "유의어|동의어|반의어|반대말", "word": "..." }
         */
        @JsonAlias({"relation", "rel_word"})
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<Relation> relation;

        // === 편의 메서드 ===

        /** 예문을 항상 List<String>으로 반환 */
        public List<String> getExample() {
            if (exampleRaw == null) return Collections.emptyList();
            List<String> out = new ArrayList<>();
            for (Object o : exampleRaw) {
                if (o == null) continue;
                if (o instanceof String s) {
                    out.add(s);
                } else if (o instanceof Map<?, ?> m) {
                    Object ex = m.get("example");
                    if (ex instanceof String s2) out.add(s2);
                }
            }
            return out;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Relation {
        private String type; // "유의어"/"동의어" 또는 "반의어"/"반대말"
        private String word;
    }
}