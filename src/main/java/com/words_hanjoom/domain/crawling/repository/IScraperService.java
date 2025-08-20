package com.words_hanjoom.domain.crawling.repository;

import com.words_hanjoom.domain.crawling.dto.response.CrawlResult;

import java.io.IOException;

public interface IScraperService {
    CrawlResult scrape(String fieldName) throws IOException;
}