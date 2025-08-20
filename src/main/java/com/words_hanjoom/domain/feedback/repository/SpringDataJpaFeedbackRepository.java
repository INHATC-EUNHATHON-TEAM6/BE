package com.words_hanjoom.domain.feedback.repository;

import com.words_hanjoom.domain.feedback.dto.response.FeedbackThisMonthActivityDto;
import com.words_hanjoom.domain.feedback.entity.ScrapActivities;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SpringDataJpaFeedbackRepository extends JpaRepository<ScrapActivities, Long>, FeedbackRepository {
    @Query("select distinct new com.words_hanjoom.domain.feedback.dto.response.FeedbackThisMonthActivityDto(s.articleId, s.activityAt) " +
            "from ScrapActivities s " +
            "where s.userId = :userId " +
            "and s.activityAt between :startDate and :endDate " +
            "order by s.activityAt")
    List<FeedbackThisMonthActivityDto> findByUserIdAndYearAndMonthAndDay(
            @Param("userId") long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
