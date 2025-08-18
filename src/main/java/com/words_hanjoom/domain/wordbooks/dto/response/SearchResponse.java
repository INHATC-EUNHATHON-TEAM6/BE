package com.words_hanjoom.domain.wordbooks.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * /search.do 응답 DTO (관대한 매핑)
 * - 일부 필드는 문서상 소문자 키(lastbuilddate 등)로 내려오므로 @JsonProperty 사용
 * - sense.link 가 문자열 또는 객체로 내려오는 케이스를 모두 처리
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchResponse {

    private Channel channel;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Channel {
        private String title;
        private String link;
        private String description;

        @JsonProperty("lastbuilddate")
        private String lastBuildDate;

        private Integer total;
        private Integer start;
        private Integer num;

        private List<Item> item;
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
        private Sense sense;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sense {
        private String definition;
        private String type;

        /** 문자열 혹은 객체로 내려올 수 있는 link 를 문자열로 통일 */
        private String link;

        @JsonSetter("link")
        public void setLinkFlexible(Object raw) {
            if (raw == null) {
                this.link = null;
                return;
            }
            if (raw instanceof String s) {
                this.link = s;
            } else if (raw instanceof Map<?, ?> m) {
                // 예: {"https://stdict...": null} 형태 등 안전 변환
                Optional<?> first = m.keySet().stream().findFirst();
                this.link = first.map(Object::toString).orElse(null);
            } else {
                this.link = raw.toString();
            }
        }
    }
}