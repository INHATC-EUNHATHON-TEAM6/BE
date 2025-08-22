package com.words_hanjoom.domain.crawling.repository;

import java.io.IOException;

public interface IPageCounter {
    int getSubCategoryPageCount(String sectionUrl) throws IOException;
}
