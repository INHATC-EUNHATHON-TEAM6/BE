package com.words_hanjoom.domain.wordbooks.repository;

import com.words_hanjoom.domain.wordbooks.entity.Wordbook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WordbookRepository extends JpaRepository<Wordbook, Long> {
    Optional<Wordbook> findByUserId(Long userId);
}