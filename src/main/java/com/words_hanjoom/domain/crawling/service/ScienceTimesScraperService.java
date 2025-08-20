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
public class ScienceTimesScraperService implements IScraperService, IPageCounter, ISubCategoryCrawl {

    private final ArticleRepository articleRepository;
    private final CrawlCategoryRepository categoryRepository;
    private final SCNewsCrawlService scNewsCrawlService;
    private static final String SCIENCETIMES_BASE_URL = "https://www.sciencetimes.co.kr/nscvrg/list/menu";

    private final Map<String, Map<String, Object>> sciencetimesFields = Map.of(
            // 사이언스 타임즈
            "기초, 응용과학", Map.of("field", "248", "subFields", new String[]{"searchCategory=220"}),
            "신소재, 신기술", Map.of("field", "250", "subFields", new String[]{"searchCategory=222"}),
            "생명과학, 의학", Map.of("field", "251", "subFields", new String[]{"searchCategory=223"}),
            "항공, 우주", Map.of("field", "252", "subFields", new String[]{"searchCategory=224"}),
            "환경, 에너지", Map.of("field", "253", "subFields", new String[]{"searchCategory=225"})
    );

    @Override
    public CrawlResult scrape(String fieldName) throws IOException {
        int savedCount = 0;
        List<SectionRequest> allArticleLinks = new ArrayList<>();

        Map<String, Object> data = sciencetimesFields.get(fieldName);
        if (data == null) {
            // 알 수 없는 카테고리일 경우 예외를 던집니다.
            throw new IllegalArgumentException("알 수 없는 카테고리: " + fieldName);
        }
        String field = (String) data.get("field");
        String[] subFields = (String[]) data.get("subFields");

        for (String subField : subFields) {
            String sectionUrl = SCIENCETIMES_BASE_URL + "/" + field + "?" + subField;

            // 뉴스기사목록 첫페이지에서 페이지 개수 추출
            int pageCount = getSubCategoryPageCount(sectionUrl);

            // 페이지 개수만큼 반복 -> 데이터 과다로 인해 서브카테고리별 5페이지로 제한
            for (int pageNo = 1; pageNo <= 5; pageNo++) {
                String paginatedUrl = SCIENCETIMES_BASE_URL + "/" + field + "?" + "thisPage=" + pageNo + "&" + subField;
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
                    savedCount += scNewsCrawlService.newsCrawl(allArticleLinks);
                    allArticleLinks.clear(); // 크롤링 후 링크 초기화
                }
            }

            try {
                savedCount = scNewsCrawlService.newsCrawl(allArticleLinks);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return new CrawlResult(savedCount);
    }

    @Override
    public int getSubCategoryPageCount(String sectionUrl) throws IOException {
        Document doc = Jsoup.connect(sectionUrl).get(); // Jsoup에 url 적용
        // 태그 요소 찾아서 마지막 페이지 번호 추출
        Element aTag = doc.selectFirst("a.page_arrow.r2");
        String lastPageNum;

        if(aTag != null) {
            String onClick = aTag.attr("onclick");
            lastPageNum = onClick.replaceAll("[^0-9]", ""); // 숫자만 추출
            System.out.println("페이지 수: " + lastPageNum);
        } else return 1; // 페이지네이션이 없으면 1페이지로 간주

        // 필터링된 문자열을 숫자로 변환
        return Integer.parseInt(lastPageNum);
    }

    // 카테고리 내의 기사들 URL 크롤링
    @Override
    public List<SectionRequest> subCategoryCrawl(String sectionUrl, String fieldName) throws IOException {
        List<SectionRequest> articleList = new ArrayList<>();   // 카테고리 기사들 링크 list화
        Long categoryId = categoryRepository.findCategoryIdByCategoryName(fieldName);   // 카테고리 이름으로 DB에서 카테고리 ID 조회
        Document doc = Jsoup.connect(sectionUrl).get(); // Jsoup에 url 적용
        Elements newsItems = doc.select("article.sel_left section.news_content div.nc_word");  // 기사 목록 영역 지정

        if (newsItems.isEmpty()) {
            return articleList; // 빈 페이지
        }

        // 1) 첫 번째 기사 URL 추출 및 정규화
        Element firstItem = newsItems.first();
        Element firstAnchor = (firstItem != null) ? firstItem.selectFirst("div.sub_txt a[href]") : null;
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
            Element a = item.selectFirst("div.sub_txt a[href]");
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
