package com.words_hanjoom.domain.crawling.service;

import com.words_hanjoom.domain.crawling.dto.request.SectionRequest;
import com.words_hanjoom.domain.crawling.dto.response.CrawlResult;
import com.words_hanjoom.domain.crawling.repository.*;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HankyungScraperService implements IScraperService, IPageCounter, ISubCategoryCrawl {

    private final ArticleRepository articleRepository;
    private final CrawlCategoryRepository categoryRepository;
    private final HKNewsCrawlService hkNewsCrawlService;
    private static final String HANKYUNG_BASE_URL = "https://www.hankyung.com";

    private final Map<String, Map<String, Object>> hankyungFields = Map.of(
            // 한국경제
            "경제", Map.of("field", "economy", "subFields", new String[]{"economic-policy", "macro", "forex", "tax", "job-welfare"}),
            "복지", Map.of("field", "economy", "subFields", new String[]{"job-welfare"}),
            "금융", Map.of("field", "financial-market", "subFields", new String[]{"financial-policy", "bank", "insurance-nbfis", "cryptocurrency-fintech", "personal-finance"}),
            "산업", Map.of("field", "industry", "subFields", new String[]{"semicon-electronics", "auto-battery", "ship-marine", "steel-chemical", "robot-future", "manage-business"}),
            "사회", Map.of("field", "society", "subFields", new String[]{"administration", "education", "employment"}),
            "문화", Map.of("field", "culture", "subFields", new String[]{})
    );



    @Override
    public CrawlResult scrape(String fieldName) throws IOException {
        int savedCount = 0;
        List<SectionRequest> allArticleLinks = new ArrayList<>();

        Map<String, Object> data = hankyungFields.get(fieldName);
        if (data == null) {
            // 알 수 없는 카테고리일 경우 예외를 던집니다.
            throw new IllegalArgumentException("알 수 없는 카테고리: " + fieldName);
        }
        String field = (String) data.get("field");
        String[] subFields = (String[]) data.get("subFields");

        for (String subField : subFields) {
            String sectionUrl = HANKYUNG_BASE_URL + "/" + field + "/" + subField;

            int pageCount = getSubCategoryPageCount(sectionUrl);

            // 페이지 개수만큼 반복 -> 데이터 과다로 인해 서브카테고리별 5페이지로 제한
            for (int pageNo = 1; pageNo <= 5; pageNo++) {
                String paginatedUrl = sectionUrl + "?page=" + pageNo;
                List<SectionRequest> links = subCategoryCrawl(paginatedUrl, fieldName);

                if (links.isEmpty()) {
                    // CategoryCrawl에서 첫 기사 중복 → 이 섹션 종료
                    System.out.printf("섹션 조기 종료: %s (page=%d)\n", sectionUrl, pageNo);
                    break;
                }

                System.out.printf("크롤링 시작: %s, 페이지: %d\n", paginatedUrl, pageNo);

                try {
                    allArticleLinks.addAll(subCategoryCrawl(paginatedUrl, fieldName));
                } catch (IOException e) {
                    System.err.printf("기사 링크 크롤링 실패: %s, 오류: %s\n", paginatedUrl, e.getMessage());
                }

                if(pageNo%10 == 0) {
                    // 10페이지마다 크롤링 진행 상황 출력
                    savedCount += hkNewsCrawlService.newsCrawl(allArticleLinks);
                    allArticleLinks.clear(); // 크롤링 후 링크 초기화
                }
            }

            try {
                savedCount += hkNewsCrawlService.newsCrawl(allArticleLinks);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return new CrawlResult(savedCount);
    }

    // 서브 카테고리 페이지 수 확인
    @Override
    public int getSubCategoryPageCount(String sectionUrl) throws IOException {
        Document doc = Jsoup.connect(sectionUrl).get(); // Jsoup에 url 적용
        Elements pagination = doc.select("div.select-paging div.page-select span.total"); // 페이지네이션 요소 선택
        if (pagination.isEmpty()) return 1; // 페이지네이션이 없으면 1페이지로 간주

        // 1. 문자열에서 숫자 부분만 추출
        String lastPageText = pagination.text().replaceAll("[^0-9]", "");

        // 2. 추출된 문자열이 비어있으면 1을 반환 (혹시 모를 오류 방지)
        if (lastPageText.isEmpty()) {
            return 1;
        }

        // 3. 필터링된 문자열을 숫자로 변환
        return Integer.parseInt(lastPageText);
    }

    // 카테고리 내의 기사들 URL 크롤링
    @Override
    public List<SectionRequest> subCategoryCrawl(String sectionUrl, String fieldName) throws IOException {
        List<SectionRequest> articleList = new ArrayList<>();   // 카테고리 기사들 링크 list화
        Long categoryId = categoryRepository.findCategoryIdByCategoryName(fieldName);   // 카테고리 이름으로 DB에서 카테고리 ID 조회
        Document doc = Jsoup.connect(sectionUrl).get(); // Jsoup에 url 적용
        Elements newsItems = doc.select("ul.news-list div.news-item");  // 기사 목록 추출

        if (newsItems.isEmpty()) {
            return articleList; // 빈 페이지
        }

        // 1) 첫 번째 기사 URL 추출 및 정규화
        Element firstItem = newsItems.first();
        Element firstAnchor = (firstItem != null) ? firstItem.selectFirst("h2.news-tit a[href]") : null;
        if (firstAnchor == null) {
            return articleList; // 기사 구조가 다르면 안전하게 스킵
        }
        String firstUrl = firstAnchor.attr("href");

        // 2) 이미 저장된 기사면 이 페이지 스킵
        if (articleRepository.existsByArticleUrl(firstUrl)) {
            System.out.printf("첫 기사 이미 저장됨. 페이지 스킵: %s (firstUrl=%s)\n", sectionUrl, firstUrl);
            return articleList; // 빈 리스트 반환 → 호출부에서 이 페이지는 넘어감
        }

        // 3) 저장되지 않은 페이지면 전체 기사 링크 수집
        for (Element item : newsItems) {
            Element a = item.selectFirst("h2.news-tit a[href]");
            if (a == null) continue;

            // sectionRequest 객체 생성
            SectionRequest sectionRequest = new SectionRequest();
            // sectionRequest에 카테고리 ID, URL 설정
            sectionRequest.setCategoryId(categoryId);
            sectionRequest.setUrl(a.attr("href"));

            // 기사정보들 list 추가
            articleList.add(sectionRequest);
        }

        return articleList;
    }
}
