package com.words_hanjoom.domain.wordbooks.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchResponse {
    private Channel channel;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Channel {
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)   // ★ 추가
        private List<Item> item;
        private int total;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        @JsonProperty("sup_no")
        private String supNo;
        private String word;
        @JsonProperty("target_code")
        private String targetCode;
        private String pos;
        private Sense sense;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sense {
        private String definition;
        private String link;
        private String type;   // (있어도 무방, 안 쓰면 그대로 둬도 됨)
    }
}