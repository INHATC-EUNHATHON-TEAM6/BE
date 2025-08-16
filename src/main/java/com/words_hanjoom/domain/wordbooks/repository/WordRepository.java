package com.words_hanjoom.domain.wordbooks.repository;

import com.words_hanjoom.domain.wordbooks.entity.Word;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WordRepository extends JpaRepository<Word, Long> {

    @Query(value = "SELECT word_id FROM words WHERE word_name = :name", nativeQuery = true)
    Optional<Long> findIdByName(@Param("name") String name);

    @Modifying
    @Query(value = """
        INSERT INTO words (word_name, synonym, antonym, definition, word_category, example, created_at)
        VALUES (:name, :syn, :ant, :def, :cat, :ex, NOW())
        ON DUPLICATE KEY UPDATE
          synonym = VALUES(synonym),
          antonym = VALUES(antonym),
          definition = VALUES(definition),
          word_category = VALUES(word_category),
          example = VALUES(example),
          word_id = LAST_INSERT_ID(word_id)
        """, nativeQuery = true)
    void upsertRaw(@Param("name") String name,
                   @Param("syn") String synonym,
                   @Param("ant") String antonym,
                   @Param("def") String definition,
                   @Param("cat") String category,
                   @Param("ex") String example);

    @Query(value = "SELECT LAST_INSERT_ID()", nativeQuery = true)
    Long lastInsertId();
}