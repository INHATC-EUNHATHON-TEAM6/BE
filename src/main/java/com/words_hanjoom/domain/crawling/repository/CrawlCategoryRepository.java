package com.words_hanjoom.domain.crawling.repository;

import com.words_hanjoom.domain.crawling.entity.CrawlCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CrawlCategoryRepository extends JpaRepository<CrawlCategory, Long> {
    Optional<CrawlCategory> findCategoryIdByCategoryName(String name);
}