package com.words_hanjoom.domain.feedback.dto.response;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackListDto {
    // key: 일(day), value: 해당 날의 활동 리스트
    private Map<String, List<FeedbackThisMonthActivityDto>> monthActivity;

    // Jackson이 Map을 직접 직렬화하도록 하는 방법
    @JsonAnyGetter
    public Map<String, List<FeedbackThisMonthActivityDto>> getMonthActivity() {
        return monthActivity;
    }
}
