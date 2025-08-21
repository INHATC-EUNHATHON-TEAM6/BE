package com.words_hanjoom.domain.users.repository;

import com.words_hanjoom.domain.users.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByCategoryIdIn(Collection<Long> ids);
}