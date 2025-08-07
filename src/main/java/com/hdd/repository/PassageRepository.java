package com.hdd.repository;

import com.hdd.entity.Passage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PassageRepository extends JpaRepository<Passage,Long> {
    List<Passage> findByCategory_CategoryIdAndSourceType(Long categoryId, String sourceType);
}
