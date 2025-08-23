package com.words_hanjoom.domain.crawling.service;

import com.words_hanjoom.domain.crawling.dto.request.SectionRequest;
import com.words_hanjoom.domain.crawling.entity.Article;
import com.words_hanjoom.domain.crawling.repository.ArticleRepository;
import com.words_hanjoom.domain.crawling.repository.INewsContentCrawl;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SCNewsCrawlService implements INewsContentCrawl {

    private final ArticleRepository articleRepository;
    private static final String SCIENCETIMES_BASE_URL = "https://www.sciencetimes.co.kr";

    @Transactional
    @Override
    public int newsCrawl(List<SectionRequest> sectionRequests) throws IOException {
        int savedCount = 0; // 저장된 기사 수를 셀 변수

        // 저장한 분야별 기사 리스트
        for (SectionRequest sectionRequest : sectionRequests) {
            // 기사 리스트에서 URL 추출
            String articleUrl = sectionRequest.getUrl();

            // 제대로된 주소가 아니라면
            if (!articleUrl.startsWith("http")) {
                articleUrl = SCIENCETIMES_BASE_URL + articleUrl;
            }

            // 중복된 기사인지 확인
            Optional<Article> existingArticle = articleRepository.findByArticleUrl(articleUrl);

            // .isPresent()는 optional 객체에 값이 있으면 true
            if (existingArticle.isPresent()) {
                System.out.println("중복된 기사: " + articleUrl);
                continue;
            }

            // 기사 파싱 및 저장
            try {
                Document doc = Jsoup.connect(articleUrl).get();

                Element titleElement = doc.selectFirst("div.sub_txt b");
                Element reporterElement = doc.selectFirst("div.atc_data a");
                Element publishedAtElement = doc.selectFirst("div.view_header div.sub_info span");
                Element contentElement = doc.selectFirst("div.atc_cont");

                String title = (titleElement != null) ? titleElement.text() : "";
                String reporterName = (reporterElement != null) ? reporterElement.text() : "";
                String publishedAt = (publishedAtElement != null) ? publishedAtElement.text() : "";
                String content = (contentElement != null) ? contentElement.text() : "";

                if (title.isEmpty() || content.isEmpty()) {
                    System.err.printf("필수 데이터 누락: %s\n", articleUrl);
                    continue;
                }

                // Article 객체 생성
                Article article = Article.builder()
                        .categoryId(sectionRequest.getCategoryId())
                        .title(title)
                        .content(content)
                        .publishedAt(publishedAt)
                        .reporterName(reporterName)
                        .publisher("사이언스타임즈")
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
