package com.words_hanjoom.domain.wordbooks.repository;

import com.words_hanjoom.domain.wordbooks.entity.WordbookWord;
import com.words_hanjoom.domain.wordbooks.entity.WordbookWordId; // ✅ 추가
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WordbookWordRepository extends JpaRepository<WordbookWord, WordbookWordId> { // ✅ ID 타입 변경


    /** 매핑 중복이면 PK 제약으로 실패하므로 INSERT IGNORE 사용 */
    @Modifying(clearAutomatically = true, flushAutomatically = true) // 권장 옵션
    @Query(value = "INSERT IGNORE INTO wordbook_words (wordbook_id, word_id) VALUES (:wbId, :wId)", nativeQuery = true)
    int insertIgnore(@Param("wbId") Long wordbookId, @Param("wId") Long wordId); // 영향 행 수 반환하도록 int 권장
}