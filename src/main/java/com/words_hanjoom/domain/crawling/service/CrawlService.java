package com.words_hanjoom.domain.crawling.service;

import com.words_hanjoom.domain.crawling.dto.request.CrawlRequest;
import com.words_hanjoom.domain.crawling.dto.response.CrawlResult;
import com.words_hanjoom.domain.crawling.entity.Article;
import com.words_hanjoom.domain.crawling.exception.CrawlException;
import com.words_hanjoom.domain.crawling.repository.ArticleRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CrawlService {
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final int TIMEOUT_MS = 15000;

    // 정규식은 미리 컴파일 (자바 문자열에서 역슬래시 2번!)
    private static final Pattern ARTICLE_URL_PATTERN =
            Pattern.compile("https://www\\.hankyung\\.com/.+?/\\d{10,}");

    private final ArticleRepository repository;

    public CrawlService(ArticleRepository articleRepository) {
        this.repository = articleRepository;
    }

    public CrawlResult crawl(CrawlRequest request) {
        if (request == null || request.sectionUrls() == null || request.sectionUrls().isEmpty()) {
            throw new CrawlException("크롤 요청에 섹션 URL이 없습니다.");
        }
        String categoryName = request.category();
        int categoryId = CategoryRegistry.categoryIdOf(categoryName);

        int saved = 0;
        for (String sectionUrl : request.sectionUrls()) {
            try {
                Set<String> links = collectArticleUrls(sectionUrl, categoryName);
                for (String url : links) {
                    try {
                        Article a = parseArticle(url, categoryId);
                        repository.save(a);
                        saved++;

                        // InterruptedException을 별도로 처리
                        try {
                            Thread.sleep(800);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt(); // 인터럽트 상태 복구
                            throw new CrawlException("크롤 중단", ie);
                        }

                    } catch (CrawlException ce) {
                        // 위에서 재throw된 인터럽트 케이스 등
                        throw ce;
                    } catch (Exception e) {
                        System.err.println("[WARN] 기사 파싱 실패: " + url + " - " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("[WARN] 섹션 로딩 실패: " + sectionUrl + " - " + e.getMessage());
            }
            // 바깥 catch(InterruptedException)는 제거됨: 이 블록에서는 던져지지 않음
        }
        return new CrawlResult(saved);
    }

    private static final Map<String, String> CATEGORY_SELECTORS = Map.of(
            "고용복지", "ul.news-list h2.news-tit a[href]",
            "복지", "ul.news-list h2.news-tit a[href]", // 복지 == 고용복
            "경제", "div.contents strong.module-tit a[href]",
            "사회", "div.contents strong.module-tit a[href]",
            "금융", "div.contents strong.module-tit a[href]",
            "산업", "div.contents strong.module-tit a[href]",
            "문화", "div.contents li h2.news-tit a[href]"
            // 다른 카테고리들도 여기에 추가
    );

    private Set<String> collectArticleUrls(String sectionUrl, String categoryName) throws IOException {
        Document doc = Jsoup.connect(sectionUrl)
                .userAgent(UA)
                .referrer("https://www.google.com")
                .timeout(TIMEOUT_MS)
                .get();

        // 카테고리별로 다른 셀렉터 사용
        String selector = CATEGORY_SELECTORS.getOrDefault(categoryName, "a[href]");
        System.out.println("[DEBUG] Using selector for " + categoryName + ": " + selector);

        Elements links = doc.select(selector);
        Set<String> urls = new LinkedHashSet<>();
        for (Element a : links) {
            String href = a.absUrl("href");
            if (isLikelyArticleUrl(href, categoryName)) {
                urls.add(stripQuery(href));
            }
        }
        return urls;
    }

    private boolean isLikelyArticleUrl(String url, String categoryName) {
        if (!url.startsWith("https://www.hankyung.com/")) return false;
        boolean looksLikeArticle = ARTICLE_URL_PATTERN.matcher(url).find();

        switch (categoryName) {
            case "고용복지":
            case "복지":
                return url.contains("/economy/job-welfare") && looksLikeArticle;
            case "경제":
                if (!url.contains("/economy")) return false;
                if (url.contains("/economy/job-welfare")) return false;
                return looksLikeArticle;
            case "사회":
                if (!url.contains("/society")) return false; return looksLikeArticle;
            case "금융":
                if (!url.contains("/financial-market")) return false; return looksLikeArticle;
            case "산업":
                if (!url.contains("/industry")) return false; return looksLikeArticle;
            case "문화":
                if (!url.contains("/culture")) return false; return looksLikeArticle;
            default:
                return looksLikeArticle;
        }
    }

    private String stripQuery(String url) {
        int i = url.indexOf('?');
        return i > 0 ? url.substring(0, i) : url;
    }

    private Article parseArticle(String url, int categoryId) throws IOException {
        Document doc = Jsoup.connect(url)
                .userAgent(UA)
                .referrer("https://www.hankyung.com/")
                .timeout(TIMEOUT_MS)
                .get();

        String title = firstNonBlank(
                meta(doc, "meta[property=og:title]"),
                meta(doc, "meta[name=title]"),
                text(doc.selectFirst("h1")),
                safe(doc.title())
        );
        String publisher = firstNonBlank(
                meta(doc, "meta[property=og:site_name]"),
                "한국경제"
        );
        String published = firstNonBlank(
                meta(doc, "meta[property=article:published_time]"),
                attr(doc.selectFirst("time[datetime]"), "datetime"),
                text(doc.selectFirst("time")),
                Instant.now().toString()
        );
        String reporter = firstNonBlank(
                meta(doc, "meta[name=byline]"),
                meta(doc, "meta[name=author]"),
                meta(doc, "meta[property=article:author]"),
                text(doc.selectFirst(".journalist, .reporter, .author, .news_byline, [rel=author], .writer"))
        );
        String content = extractBody(doc);

        Article a = new Article();
        a.setCategoryId(categoryId);
        a.setTitle(nz(title));
        a.setContent(nz(content));
        a.setPublishedAt(nz(published));
        a.setReporterName(nz(reporter));
        a.setPublisher(nz(publisher));
        a.setArticleUrl(url);
        a.setCreatedAt(Instant.now());
        a.setDeletedAt(null);
        return a;
    }

    private String extractBody(Document doc) {
        List<String> containers = List.of(
                "article",
                ".article", ".article-body", ".article_body", ".articleContent",
                "#articletxt", "#article-body", "#newsView",
                ".news-article", ".news_article", ".news_view", ".news-body", ".entry-content"
        );
        for (String sel : containers) {
            Elements box = doc.select(sel);
            if (!box.isEmpty()) {
                Elements ps = box.select("p");
                if (!ps.isEmpty()) {
                    String joined = ps.stream()
                            .map(this::text)
                            .map(this::clean)
                            .filter(s -> s != null && !s.isBlank())
                            .collect(Collectors.joining(" "));
                    if (!joined.isBlank()) return joined;
                }
                String fallback = clean(box.text());
                if (fallback != null && !fallback.isBlank()) return fallback;
            }
        }
        Elements ps = doc.select("p");
        if (!ps.isEmpty()) {
            String joined = ps.stream()
                    .map(this::text)
                    .map(this::clean)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(" "));
            if (!joined.isBlank()) return joined;
        }
        return "";
    }

    // utils
    private String meta(Document doc, String selector) {
        Element m = doc.selectFirst(selector);
        return m != null ? m.attr("content") : null;
    }
    private String attr(Element e, String attr) { return e != null ? e.attr(attr) : null; }
    private String text(Element e) { return e != null ? e.text() : null; }
    private String safe(String s) { return s == null ? null : s; }
    private String nz(String s) { return s == null ? "" : s; }
    private String firstNonBlank(String... arr) { for (String s : arr) if (s != null && !s.isBlank()) return s; return null; }

    private String clean(String s) {
        if (s == null) return null;
        // NBSP 정리 + 정규식은 꼭 이스케이프 2번
        String t = s.replace('\u00A0', ' ')
                .replaceAll("[ \\t\\u3000]+", " ")
                .replaceAll("\\s*\\n\\s*", " ")
                .trim();
        return t;
    }
}
