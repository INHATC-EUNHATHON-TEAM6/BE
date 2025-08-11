package com.words_hanjoom.domain.crawling.repository;

import com.words_hanjoom.domain.crawling.entity.Article;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface ArticleRepository {
    void save(Article article);
    default void saveAll(Collection<Article> articles) {
        if (articles == null) return;
        for (Article a : articles) save(a);
    }
}
