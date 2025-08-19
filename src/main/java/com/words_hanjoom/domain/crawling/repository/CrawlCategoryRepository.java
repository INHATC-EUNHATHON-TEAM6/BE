package com.words_hanjoom.domain.crawling.repository;

import com.words_hanjoom.domain.crawling.entity.CrawlCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CrawlCategoryRepository extends JpaRepository<CrawlCategory, Long> {

    @Query("select c.categoryId from CrawlCategory c where c.categoryName = :name")
    Long findCategoryIdByCategoryName(String name);
}