package com.words_hanjoom.infra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NiklDictionaryClient {

    private final RestClient restClient = RestClient.create();

    @Value("${nikl.base-url:https://opendict.korean.go.kr/api/search}")
    private String baseUrl;

    @Value("${nikl.api-key}")
    private String apiKey;

    public Optional<String> findLemma(String surfaceForm) {
        try {
            String q = URLEncoder.encode(surfaceForm, StandardCharsets.UTF_8);
            String url = String.format("%s?key=%s&q=%s&req_type=json&part=word&per_page=20",
                    baseUrl, apiKey, q);

            NiklSearchResponse resp = restClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(NiklSearchResponse.class);

            if (resp == null || resp.channel == null || resp.channel.items == null || resp.channel.items.isEmpty()) {
                return Optional.empty();
            }

            List<NiklItem> items = resp.channel.items;

            Optional<NiklItem> exact = items.stream()
                    .filter(i -> surfaceForm.equals(i.word))
                    .findFirst();
            if (exact.isPresent()) return Optional.of(exact.get().word);

            return items.stream()
                    .sorted(Comparator.comparingInt((NiklItem i) -> i.word != null && i.word.endsWith("다") ? 2 : 1)
                            .reversed())
                    .map(i -> i.word)
                    .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NiklSearchResponse { public Channel channel; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Channel { public List<NiklItem> items; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NiklItem { @JsonProperty("word") public String word; }
}