package com.words_hanjoom.domain.wordbooks.repository;

import com.words_hanjoom.domain.wordbooks.entity.WordbookWord;
import com.words_hanjoom.domain.wordbooks.entity.WordbookWordId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WordbookWordRepository extends JpaRepository<WordbookWord, WordbookWordId> {
}