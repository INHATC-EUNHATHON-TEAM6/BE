// com.words_hanjoom.infra.dictionary.AiDictionaryClient.java
package com.words_hanjoom.infra.dictionary;

import com.words_hanjoom.domain.wordbooks.dto.response.DictEntry;
import reactor.core.publisher.Mono;

public interface AiDictionaryClient {
    Mono<DictEntry> defineFromAi(String surface, String context);
}