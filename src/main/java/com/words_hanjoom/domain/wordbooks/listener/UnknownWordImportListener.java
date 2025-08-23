// src/main/java/com/words_hanjoom/domain/wordbooks/listener/UnknownWordImportListener.java
package com.words_hanjoom.domain.wordbooks.listener;

import com.words_hanjoom.domain.crawling.entity.Article;
import com.words_hanjoom.domain.crawling.repository.ArticleRepository;
import com.words_hanjoom.domain.feedback.entity.ActivityType;
import com.words_hanjoom.domain.feedback.event.ScrapActivitySavedEvent;
import com.words_hanjoom.domain.wordbooks.service.UnknownWordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnknownWordImportListener {

    private final UnknownWordService unknownWordService;
    private final ArticleRepository articleRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW) // ★ 여기서 새 트랜잭션 시작
    public void handle(ScrapActivitySavedEvent event) {
        if (event.comparisonType() != ActivityType.UNKNOWN_WORD) return;

        // 기사 본문 로드 → 컨텍스트 생성
        String context = articleRepository.findById(event.articleId())
                .map(a -> (nz(a.getTitle()) + " " + nz(a.getContent())).trim())
                .orElse("");

        log.info("[EVT] AFTER_COMMIT received: type={}, userId={}, answer={}",
                event.comparisonType(), event.userId(), event.userAnswer());
        unknownWordService.importUnknownWords(event.userId(), event.userAnswer(), context);
    }

    private static String nz(String s){ return s == null ? "" : s; }
}
