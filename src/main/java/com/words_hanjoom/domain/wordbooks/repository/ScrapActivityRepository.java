package com.words_hanjoom.domain.wordbooks.repository;

import com.words_hanjoom.domain.wordbooks.entity.ScrapActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScrapActivityRepository extends JpaRepository<ScrapActivity, Long> {

    List<ScrapActivity> findByComparisonType(ScrapActivity.ComparisonType comparisonType);

    List<ScrapActivity> findByComparisonTypeAndUserAnswerIsNotNull(
            ScrapActivity.ComparisonType comparisonType
    );

    @Query(value = """
        select * 
        from scrap_activities
        where comparison_type = :type
          and user_answer is not null
        """, nativeQuery = true)
    List<ScrapActivity> findUnknownsNative(@Param("type") String type);
}