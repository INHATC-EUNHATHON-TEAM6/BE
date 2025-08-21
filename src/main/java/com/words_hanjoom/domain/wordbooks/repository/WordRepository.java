package com.words_hanjoom.domain.wordbooks.repository;

import com.words_hanjoom.domain.wordbooks.entity.Word;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Repository
public interface WordRepository extends JpaRepository<Word, Long> {

    Optional<Word> findByWordName(String wordName);
    @Query(value = """
        SELECT * FROM words
        WHERE word_name = :surface
           OR REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(word_name,
               '-', ''), '·', ''), 'ㆍ', ''), '‐', ''), '–', ''), '—', '') = :plain
        LIMIT 1
        """, nativeQuery = true)
    Optional<Word> findLooselyByName(String surface, String plain);

    // openai UnknownService 폴백 연결
    @Query("select max(w.senseNo) from Word w where w.targetCode = :tc")
    Integer findMaxSenseNoByTargetCode(@Param("tc") Long targetCode);
}