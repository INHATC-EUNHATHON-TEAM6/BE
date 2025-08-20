package com.words_hanjoom.domain.feedback.repository;

import com.words_hanjoom.domain.feedback.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataJpaCategoryRepository  extends JpaRepository<Category, Long> {
}
