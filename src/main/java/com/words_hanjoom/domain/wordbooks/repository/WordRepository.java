package com.words_hanjoom.domain.wordbooks.repository;

import com.words_hanjoom.domain.wordbooks.entity.Word;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WordRepository extends JpaRepository<Word, Integer> {
    Optional<Word> findByWordName(String wordName);
}