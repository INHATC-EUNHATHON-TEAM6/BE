// src/main/java/com/words_hanjoom/domain/feedback/event/ScrapActivitySavedEvent.java
package com.words_hanjoom.domain.feedback.event;

import com.words_hanjoom.domain.feedback.entity.ActivityType;

public record ScrapActivitySavedEvent(
        Long userId,
        Long articleId,
        ActivityType comparisonType,
        String userAnswer
) {}
