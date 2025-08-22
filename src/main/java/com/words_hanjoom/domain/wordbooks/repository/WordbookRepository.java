package com.words_hanjoom.domain.wordbooks.repository;

import com.words_hanjoom.domain.wordbooks.entity.Wordbook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WordbookRepository extends JpaRepository<Wordbook, Long> {
    // 사용자별 단어장 조회
    Optional<Wordbook> findByUserId(Long userId);

    // 엔티티 get-or-create
    default Wordbook getOrCreateEntity(Long userId) {
        return findByUserId(userId).orElseGet(() -> save(Wordbook.builder().userId(userId).build()));
    }
}