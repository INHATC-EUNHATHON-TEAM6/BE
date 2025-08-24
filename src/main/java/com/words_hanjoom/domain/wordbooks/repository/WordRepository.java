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

    // 동일 표기 전부
    List<Word> findAllByWordName(String wordName);

    Optional<Word> findByTargetCodeAndSenseNo(Long targetCode, Integer senseNo);

    // 단건 느슨한 매칭
    @Query(value = """
        SELECT * FROM words
        WHERE word_name = :surface
           OR REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(word_name,
               '-', ''), '·', ''), 'ㆍ', ''), '‐', ''), '–', ''), '—', '') = :plain
        LIMIT 1
        """, nativeQuery = true)
    Optional<Word> findLooselyByName(@Param("surface") String surface,
                                     @Param("plain") String plain);

    // 다건 느슨한 매칭
    @Query(value = """
        SELECT * FROM words
        WHERE word_name = :surface
           OR REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(word_name,
               '-', ''), '·', ''), 'ㆍ', ''), '‐', ''), '–', ''), '—', '') = :plain
        """, nativeQuery = true)
    List<Word> findLooselyByNameMulti(@Param("surface") String surface,
                                      @Param("plain") String plain);

    // AI senseNo 전역 할당용
    @Query("select max(w.senseNo) from Word w where w.targetCode = :tc")
    Integer findMaxSenseNoByTargetCode(@Param("tc") Long targetCode);
}