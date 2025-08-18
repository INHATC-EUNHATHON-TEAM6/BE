package com.words_hanjoom.infra.dictionary;

import com.words_hanjoom.domain.wordbooks.dto.request.SearchRequest;
import com.words_hanjoom.domain.wordbooks.dto.response.SearchResponse;
import com.words_hanjoom.domain.wordbooks.dto.response.ViewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NiklDictionaryClientImpl implements NiklDictionaryClient {

    private final WebClient dicWebClient;

    @Value("${nikl.api.key}")
    private String apiKey;

    // ---- reactive ----
    @Override
    public Mono<SearchResponse> search(SearchRequest req) {
        return dicWebClient.get()
                .uri(uri -> uri.path("/search.do")
                        .queryParam("key", apiKey)
                        .queryParam("q", req.q())
                        .queryParam("req_type", req.reqType())
                        .queryParam("start", req.start())
                        .queryParam("num", req.num())
                        .queryParam("advanced", req.advanced())
                        .queryParamIfPresent("target", req.target())
                        .queryParamIfPresent("method", req.method())
                        .queryParamIfPresent("type1", req.type1())
                        .queryParamIfPresent("type2", req.type2())
                        .queryParamIfPresent("pos", req.pos())
                        .queryParamIfPresent("cat", req.cat())
                        .queryParamIfPresent("multimedia", req.multimedia())
                        .queryParamIfPresent("letter_s", req.letterS())
                        .queryParamIfPresent("letter_e", req.letterE())
                        .queryParamIfPresent("update_s", req.updateS())
                        .queryParamIfPresent("update_e", req.updateE())
                        .build())
                .retrieve()
                .bodyToMono(SearchResponse.class);
    }

    @Override
    public Mono<ViewResponse> view(long targetCode) {
        return dicWebClient.get()
                .uri(uri -> uri.path("/view.do")
                        .queryParam("key", apiKey)
                        .queryParam("target_code", targetCode)
                        .queryParam("req_type", "json")
                        .build())
                .retrieve()
                .bodyToMono(ViewResponse.class);
    }

    // ---- facade (blocking) ----

    @Override
    public Optional<String> findLemma(String surface) {
        var req = new SearchRequest(
                surface,                 // q
                "json",                  // reqType
                1,                       // start
                1,                       // num
                "n",                     // advanced
                Optional.of(1),          // target (표제어)
                Optional.of("exact"),    // method (일치)
                Optional.empty(),        // type1
                Optional.empty(),        // type2
                Optional.empty(),        // pos
                Optional.empty(),        // cat
                Optional.empty(),        // multimedia
                Optional.empty(),        // letterS
                Optional.empty(),        // letterE
                Optional.empty(),        // updateS
                Optional.empty()         // updateE
        );

        var sr = search(req).block(Duration.ofSeconds(5));
        if (sr == null || sr.getChannel() == null || sr.getChannel().getItem() == null) return Optional.empty();
        return sr.getChannel().getItem().stream().findFirst().map(i -> i.getWord());
    }

    @Override
    public Optional<Lexeme> lookup(String lemma) {
        var req = new SearchRequest(
                lemma,               // q
                "json",              // reqType
                1,                   // start
                1,                   // num
                "n",                 // advanced
                Optional.of(1),      // target (표제어)
                Optional.of("exact"),// method (일치)
                Optional.empty(),    // type1
                Optional.empty(),    // type2
                Optional.empty(),    // pos
                Optional.empty(),    // cat
                Optional.empty(),    // multimedia
                Optional.empty(),    // letterS
                Optional.empty(),    // letterE
                Optional.empty(),    // updateS
                Optional.empty()     // updateE
        );

        var sr = search(req).block(Duration.ofSeconds(5));
        if (sr == null || sr.getChannel() == null || sr.getChannel().getItem() == null || sr.getChannel().getItem().isEmpty())
            return Optional.empty();

        long targetCode = sr.getChannel().getItem().get(0).getTargetCode();

        var vr = view(targetCode).block(Duration.ofSeconds(5));
        if (vr == null || vr.getChannel() == null || vr.getChannel().getItem() == null || vr.getChannel().getItem().isEmpty())
            return Optional.empty();

        var first = vr.getChannel().getItem().get(0);
        if (first.getSense() == null || first.getSense().isEmpty()) return Optional.empty();

        var s = first.getSense().get(0);
        byte shoulderNo = NiklDictionaryClient.parseByteOrZero(first.getSupNo());

        var synonyms   = java.util.List.<String>of();
        var antonyms   = java.util.List.<String>of();
        var examples   = java.util.List.<String>of();
        var categories = (s.getCat() == null || s.getCat().isBlank())
                ? java.util.List.<String>of()
                : java.util.List.of(s.getCat());

        var lex = new Lexeme(
                first.getWord(),
                synonyms,
                antonyms,
                s.getDefinition(),
                categories,
                examples,
                shoulderNo
        );
        return Optional.of(lex);
    }
}
