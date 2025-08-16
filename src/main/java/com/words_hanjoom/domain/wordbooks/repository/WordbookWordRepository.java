package com.words_hanjoom.domain.wordbooks.repository;

import com.words_hanjoom.domain.wordbooks.entity.WordbookWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WordbookWordRepository extends JpaRepository<WordbookWord, Long> {

    /** 매핑 중복이면 PK 제약으로 실패하므로 INSERT IGNORE 사용 */
    @Modifying
    @Query(value = "INSERT IGNORE INTO wordbook_words (wordbook_id, word_id) VALUES (:wbId, :wId)", nativeQuery = true)
    void insertIgnore(@Param("wbId") Long wordbookId, @Param("wId") Long wordId);
}