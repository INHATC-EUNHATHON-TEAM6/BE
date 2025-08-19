package com.words_hanjoom.infra.dictionary;

import com.words_hanjoom.domain.wordbooks.dto.request.SearchRequest;
import com.words_hanjoom.domain.wordbooks.dto.response.DictEntry;
import com.words_hanjoom.domain.wordbooks.dto.response.SearchResponse;
import com.words_hanjoom.domain.wordbooks.dto.response.ViewResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

public interface NiklDictionaryClient {

    // --- Reactive API ---
    Mono<SearchResponse> search(SearchRequest req);
    Mono<ViewResponse> view(long targetCode);

    Mono<DictEntry> quickLookup(String q);

    // --- Facade (blocking) ---
    Optional<String> findLemma(String surface);
    Optional<Lexeme> lookup(String lemma);

    // --- helpers
    static byte parseByteOrZero(String s) {
        try { return (s == null || s.isBlank()) ? 0 : Byte.parseByte(s.trim()); }
        catch (Exception e) { return 0; }
    }
    static short parseShortOrOne(String s) {
        try { return (s == null || s.isBlank()) ? 1 : Short.parseShort(s.trim()); }
        catch (Exception e) { return 1; }
    }

    /**
     * 사전에서 뽑아온 정규화된 어휘 단위
     */
    final class Lexeme {
        private final String word;
        private final List<String> synonyms;
        private final List<String> antonyms;
        private final String definition;
        private final List<String> categories;
        private final List<String> examples;
        private final byte shoulderNo;   // sup_no
        private final long targetCode;   // target_code
        private final short senseNo;     // sense_no (없으면 1로 디폴트)

        public Lexeme(
                String word,
                List<String> synonyms,
                List<String> antonyms,
                String definition,
                List<String> categories,
                List<String> examples,
                byte shoulderNo,
                long targetCode,
                short senseNo
        ) {
            this.word = word;
            this.synonyms = synonyms;
            this.antonyms = antonyms;
            this.definition = definition;
            this.categories = categories;
            this.examples = examples;
            this.shoulderNo = shoulderNo;
            this.targetCode = targetCode;
            this.senseNo = senseNo;
        }

        public String word() { return word; }
        public List<String> synonyms() { return synonyms; }
        public List<String> antonyms() { return antonyms; }
        public String definition() { return definition; }
        public List<String> categories() { return categories; }
        public List<String> examples() { return examples; }
        public byte shoulderNo() { return shoulderNo; }
        public long targetCode() { return targetCode; }
        public short senseNo() { return senseNo; }
    }
}