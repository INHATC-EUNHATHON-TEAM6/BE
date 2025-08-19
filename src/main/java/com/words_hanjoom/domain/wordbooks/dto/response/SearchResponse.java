package com.words_hanjoom.domain.wordbooks.dto.response;

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
        private int total;
        private List<Item> item;
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
        private String type;
    }
}