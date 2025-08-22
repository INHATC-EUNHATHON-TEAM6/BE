package com.words_hanjoom.domain.crawling.repository;

import com.words_hanjoom.domain.crawling.dto.request.SectionRequest;

import java.io.IOException;
import java.util.List;

public interface ISubCategoryCrawl {
    List<SectionRequest> subCategoryCrawl(String sectionUrl, String fieldName) throws IOException;
}
