package com.words_hanjoom.domain.wordbooks.repository;

import com.words_hanjoom.domain.wordbooks.entity.Wordbook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WordbookRepository extends JpaRepository<Wordbook, Long> {

    @Query("select wb from Wordbook wb where wb.userId = :userId")
    Optional<Wordbook> findByUserId(@Param("userId") Long userId);

    default Wordbook ensureWordbook(Long userId) {
        return findByUserId(userId)
                .orElseGet(() -> save(Wordbook.builder().userId(userId).build()));
    }
}