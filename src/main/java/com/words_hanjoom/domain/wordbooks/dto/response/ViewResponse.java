package com.words_hanjoom.domain.wordbooks.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * /view.do 응답 DTO (상세)
 * - 한 target_code 에 여러 의미(sense)가 포함될 수 있음
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ViewResponse {

    private Channel channel;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Channel {
        private List<Item> item; // API 특성상 리스트로 내려오는 케이스 존재
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

        // 상세는 의미가 여러 개인 배열
        private List<Sense> sense;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sense {
        @JsonProperty("sense_no")
        private String senseNo;

        private String definition;
        private String type;                  // 범주(일반어 등)
        private String origin;                // 원어
        private String cat;                   // 전문 분야

        // 문형/문법
        private String syntacticArgument;     // 문형
        private String syntacticAnnotation;   // 문법

        // 필요 시: 발음/용례 등 추가 필드 확장 가능
        // private List<Example> example; ...
    }
}
