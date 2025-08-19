package com.words_hanjoom.domain.crawling.service;

import com.words_hanjoom.domain.crawling.dto.request.SectionRequest;
import com.words_hanjoom.domain.crawling.dto.response.CrawlResult;
import com.words_hanjoom.domain.crawling.entity.Article;
import com.words_hanjoom.domain.crawling.repository.ArticleRepository;
import com.words_hanjoom.domain.crawling.repository.CrawlCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.data.web.PageableArgumentResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class HankyungScraperService {

    private final ArticleRepository articleRepository;
    private final CrawlCategoryRepository categoryRepository;
    private final Random random = new Random();
    private static final String HANKYUNG_BASE_URL = "https://www.hankyung.com";

    private final Map<String, Map<String, Object>> hankyungFields = Map.of(
            "경제", Map.of("field", "economy", "subFields", new String[]{"economic-policy", "macro", "forex", "tax", "job-welfare"}),
            "복지", Map.of("field", "economy", "subFields", new String[]{"job-welfare"}),
            "금융", Map.of("field", "financial-market", "subFields", new String[]{"financial-policy", "bank", "insurance-nbfis", "cryptocurrency-fintech", "personal-finance"}),
            "산업", Map.of("field", "industry", "subFields", new String[]{"semicon-electronics", "auto-battery", "ship-marine", "steel-chemical", "robot-future", "manage-business"}),
            "사회", Map.of("field", "society", "subFields", new String[]{"administration", "education", "employment"}),
            "문화", Map.of("field", "culture", "subFields", new String[]{})
    );

    public CrawlResult scrapeHankyung(String fieldName) throws IOException {
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

            for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
                String paginatedUrl = sectionUrl + "?page=" + pageNo;
                System.out.printf("크롤링 시작: %s, 페이지: %d\n", paginatedUrl, pageNo);

                //todo:  이미 파싱했던 기사가 있는 페이지는 긁지 않기

                try {
                    allArticleLinks.addAll(CategoryCrawl(paginatedUrl, fieldName));
                } catch (IOException e) {
                    System.err.printf("기사 링크 크롤링 실패: %s, 오류: %s\n", paginatedUrl, e.getMessage());
                }
            }

            try {
                savedCount = NewsCrawl(allArticleLinks);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return new CrawlResult(savedCount);
    }

    // 서브 카테고리 페이지 수 확인
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
    public List<SectionRequest> CategoryCrawl(String sectionUrl, String fieldName) throws IOException {
        List<SectionRequest> articleList = new ArrayList<>();   // 카테고리 기사들 링크 list화
        Long categoryId = categoryRepository.findCategoryIdByCategoryName(fieldName);   // 카테고리 이름으로 DB에서 카테고리 ID 조회
        Document doc = Jsoup.connect(sectionUrl).get(); // Jsoup에 url 적용
        Elements newsItems = doc.select("ul.news-list div.news-item");  // 기사 목록 추출

        // 각 기사에 대해 URL과 제목 추출
        for (Element item : newsItems) {
            // sectionRequest 객체 생성
            SectionRequest sectionRequest = new SectionRequest();

            // sectionRequest에 카테고리 ID, URL 설정
            sectionRequest.setCategoryId(categoryId);
            sectionRequest.setUrl(item.selectFirst("h2.news-tit a[href]").attr("href"));

            // 기사정보들 list 추가
            articleList.add(sectionRequest);
        }

        return articleList;
    }

    @Transactional
    public int NewsCrawl(List<SectionRequest> sectionRequests) throws IOException {
        int savedCount = 0; // 저장된 기사 수를 셀 변수

        // 저장한 분야별 기사 리스트
        for (SectionRequest sectionRequest : sectionRequests) {
            // 기사 리스트에서 URL 추출
            String articleUrl = sectionRequest.getUrl();

            if (!articleUrl.startsWith("http")) {
                articleUrl = HANKYUNG_BASE_URL + articleUrl;
            }

            if (articleRepository.existsByArticleUrl(articleUrl)) {
                System.out.println("중복된 기사: " + articleUrl);
                // 중복된 기사가 10개 이상 반복될 경우 이 데이터셋을 넘기고 다음 분야로 크롤링 데이터를 pass하는게 좋을지?
                continue;
            }

            try {
                Document doc = Jsoup.connect(articleUrl).get();

                // ... (기존의 데이터 추출 및 유효성 검사 로직)
                Element titleElement = doc.selectFirst("h1.headline");
                Element reporterElement = doc.selectFirst("div.author a.item");
                Element publishedAtElement = doc.selectFirst("div.datetime span.txt-date");
                Element contentElement = doc.selectFirst("div.article-body");

                String title = (titleElement != null) ? titleElement.text() : "";
                String reporterName = (reporterElement != null) ? reporterElement.text() : "";
                String publishedAt = (publishedAtElement != null) ? publishedAtElement.text() : "";
                String content = (contentElement != null) ? contentElement.text() : "";

                if (title.isEmpty() || content.isEmpty()) {
                    System.err.printf("필수 데이터 누락: %s\n", articleUrl);
                    continue;
                }

                // Article 객체 생성 및 DB에 저장
                Article article = Article.builder()
                        .categoryId(sectionRequest.getCategoryId())
                        .title(title)
                        .content(content)
                        .publishedAt(publishedAt)
                        .reporterName(reporterName)
                        .publisher("한국경제")
                        .articleUrl(articleUrl)
                        .createdAt(java.time.LocalDateTime.now()) // 현재 시각으로 생성
                        .deletedAt(null) // 소프트 삭제용 필드, null이면 유효
                        .build();

                articleRepository.save(article);
                System.out.printf("크롤링 완료: %s, 제목: %s\n", articleUrl, title);

                savedCount++; // 저장 성공 시 카운터 증가

            } catch (Exception e) {
                System.err.printf("기사 파싱 실패: %s, 오류: %s\n", articleUrl, e.getMessage());
            }
        }

        return savedCount; // 최종 저장된 기사 수 반환
    }
}
