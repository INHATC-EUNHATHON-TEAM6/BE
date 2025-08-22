// src/main/java/com/words_hanjoom/domain/wordbooks/listener/UnknownWordImportListener.java
package com.words_hanjoom.domain.wordbooks.listener;

import com.words_hanjoom.domain.feedback.entity.ActivityType;
import com.words_hanjoom.domain.feedback.event.ScrapActivitySavedEvent;
import com.words_hanjoom.domain.wordbooks.service.UnknownWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UnknownWordImportListener {

    private final UnknownWordService unknownWordService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ScrapActivitySavedEvent event) {
        if (event.comparisonType() != ActivityType.UNKNOWN_WORD) return;
        unknownWordService.importUnknownWords(event.userId(), event.userAnswer());
    }
}
