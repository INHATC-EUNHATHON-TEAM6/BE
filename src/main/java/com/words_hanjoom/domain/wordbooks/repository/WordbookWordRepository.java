package com.words_hanjoom.domain.wordbooks.repository;

import com.words_hanjoom.domain.wordbooks.dto.response.WordItemDto;
import com.words_hanjoom.domain.wordbooks.entity.WordbookWord;
import com.words_hanjoom.domain.wordbooks.entity.WordbookWordId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface WordbookWordRepository extends JpaRepository<WordbookWord, WordbookWordId> {

    @Query(value = """
      select new com.words_hanjoom.domain.wordbooks.dto.response.WordItemDto(
          w.wordId, w.wordName, w.definition, w.example,
          w.wordCategory, w.synonym, w.antonym, w.shoulderNo
      )
      from WordbookWord ww
        join ww.wordbook wb
        join ww.word w
      where wb.userId = :userId
        and w.deletedAt is null
      """,
            countQuery = """
      select count(ww)
      from WordbookWord ww
        join ww.wordbook wb
        join ww.word w
      where wb.userId = :userId
        and w.deletedAt is null
      """)
    Page<WordItemDto> findMyWords(@Param("userId") Long userId, Pageable pageable);

    /** 매핑 중복이면 PK 제약으로 실패하므로 INSERT IGNORE 사용 (created_at도 같이 넣기) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
            value = """
        INSERT IGNORE INTO wordbook_words (wordbook_id, word_id, created_at)
        VALUES (:wbId, :wId, CURRENT_TIMESTAMP)
        """,
            nativeQuery = true
    )
    int insertIgnore(@Param("wbId") Long wordbookId, @Param("wId") Long wordId);
}
