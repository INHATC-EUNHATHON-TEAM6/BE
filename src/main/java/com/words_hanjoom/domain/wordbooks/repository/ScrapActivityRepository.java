package com.words_hanjoom.domain.wordbooks.repository;

import com.words_hanjoom.domain.wordbooks.entity.ScrapActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScrapActivityRepository extends JpaRepository<ScrapActivity, Long> {

    // UNKNOWN_WORD 중에서 ai_answer/ai_feedback 이 모두 채워진 건만 수집
    @Query(value = """
        SELECT * FROM scrap_activities
        WHERE comparison_type = :type
          AND ai_answer   IS NOT NULL AND ai_answer   <> ''
          AND ai_feedback IS NOT NULL AND ai_feedback <> ''
        ORDER BY scrap_id
        """, nativeQuery = true)

    List<ScrapActivity> findUnknownsNative(@Param("type") String type);
}