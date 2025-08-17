package com.words_hanjoom.domain.feedback.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.words_hanjoom.domain.feedback.dto.request.ScrapActivityDto;
import com.words_hanjoom.domain.feedback.dto.response.FeedbackDto;
import com.words_hanjoom.domain.feedback.entity.ActivityType;
import com.words_hanjoom.domain.feedback.entity.Article;
import com.words_hanjoom.domain.feedback.entity.Category;
import com.words_hanjoom.domain.feedback.entity.ScrapActivities;
import com.words_hanjoom.domain.feedback.repository.FeedbackRepository;
import com.words_hanjoom.domain.feedback.repository.SpringDataJpaFeedbackRepository;
import jakarta.transaction.Transactional;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

import static com.words_hanjoom.domain.feedback.service.FeedbackServicePromptTemplate.SCRAP_FEEDBACK_SYSTEM_PROMPT;
import static com.words_hanjoom.domain.feedback.service.FeedbackServicePromptTemplate.SCRAP_FEEDBACK_USER_PROMPT;

@Service
@Transactional
public class FeedbackService {
    private final FeedbackRepository feedbackRepository;
    private final ObjectMapper objectMapper;
    private final OpenAiChatModel chatModel;
    private final OpenAiEmbeddingModel embeddingModel;


    @Autowired
    public FeedbackService(FeedbackRepository springDataJpaFeedbackRepository,
                           ObjectMapper objectMapper,
                           OpenAiChatModel chatModel,
                           OpenAiEmbeddingModel embeddingModel) {
        this.feedbackRepository = springDataJpaFeedbackRepository;
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    public List<FeedbackDto> feedbackScrapActivity(ScrapActivityDto activity) throws JsonProcessingException {
        LocalDateTime createAt = LocalDateTime.now();
        Long userId = 2L;
        Article article = getArticleHardcoding();
        List<FeedbackDto> feedbacks = new ArrayList<>();
        Map<String, Object> requests = requestFeedbackByAI(activity, article);
        Map<String, Object> aiFeedbacks = (Map<String, Object>)requests.get("feedbacks");
        feedbacks.add(compareTitle(
                activity.getTitle(),
                (String)requests.get("title"),
                (String)aiFeedbacks.get("title"))
        );
        feedbacks.add(compareSummary(
                activity.getSummary(),
                (String)requests.get("summary"),
                (String)aiFeedbacks.get("summary"))
        );
        feedbacks.add(compareCategory(
                activity.getCategory(),
                (String)requests.get("category"),
                (String)aiFeedbacks.get("category"))
        );
        feedbacks.add(compareKeywords(
                activity.getKeywords(),
                (List<String>)requests.get("keywords"),
                (String)aiFeedbacks.get("keywords"))
        );
        feedbacks.add(getWordsToLearn(activity.getVocabularies()));
        feedbacks.add(checkUserComment(
                activity.getComment(),
                (String)requests.get("tendency"),
                (String)aiFeedbacks.get("comment"))
        );
        for (int i=0; i<feedbacks.size(); i++) {
            FeedbackDto tmp = feedbacks.get(i);
            ScrapActivities scrapActivities = new ScrapActivities();
            scrapActivities.setUserId(userId);
            scrapActivities.setArticleId(article.getArticleId());
            scrapActivities.setComparisonType(tmp.getActivityType());
            scrapActivities.setUserAnswer(tmp.getUserAnswer());
            scrapActivities.setAiAnswer(tmp.getAiAnswer());
            scrapActivities.setAiFeedback(tmp.getAiFeedback());
            scrapActivities.setEvaluationScore(tmp.getEvaluationScore());
            scrapActivities.setActivityAt(createAt);
            feedbackRepository.save(scrapActivities);
        }
        return feedbacks;
    }

    private FeedbackDto compareTitle(String userTitle, String aiTitle, String aiFeedback) {
        EmbeddingResponse userTitleEmbeddingResponse = embeddingModel.embedForResponse(List.of(userTitle));
        EmbeddingResponse aiTitleEmbeddingResponse = embeddingModel.embedForResponse(List.of(aiTitle));
        float[] userTitleVector = userTitleEmbeddingResponse.getResults().get(0).getOutput();
        float[] aiTitleVector = aiTitleEmbeddingResponse.getResults().get(0).getOutput();
        double score = (CosineSimilarity.calculate(userTitleVector, aiTitleVector) + 1) / 2.0;
        return new FeedbackDto(ActivityType.TITLE, userTitle, aiTitle, aiFeedback, Double.toString(score));
    }

    private FeedbackDto compareSummary(String userSummary, String aiSummary, String aiFeedback) {
        double score = RougeCalculator.calculateRouge2(userSummary, aiSummary).getFMeasure();
        return new FeedbackDto(ActivityType.SUMMARY, userSummary, aiSummary, aiFeedback, Double.toString(score));
    }

    private FeedbackDto compareCategory(String userCategory, String aiCategory, String aiFeedback) {
        EmbeddingResponse userCategoryEmbeddingResponse = embeddingModel.embedForResponse(List.of(userCategory));
        EmbeddingResponse aiCategoryEmbeddingResponse = embeddingModel.embedForResponse(List.of(aiCategory));
        float[] userCategoryVector = userCategoryEmbeddingResponse.getResults().get(0).getOutput();
        float[] aiCategoryVector = aiCategoryEmbeddingResponse.getResults().get(0).getOutput();
        double score = (CosineSimilarity.calculate(userCategoryVector, aiCategoryVector) + 1) / 2.0;
        return new FeedbackDto(ActivityType.CATEGORY, userCategory, aiCategory, aiFeedback, Double.toString(score));
    }

    private FeedbackDto compareKeywords(List<String> userKeywords, List<String> aiKeywords, String aiFeedback) {
        Set<String> userKeywordsSet = new HashSet<>(userKeywords);
        Set<String> aiKeywordsSet = new HashSet<>(aiKeywords);
        double score = SetSimilarityCalculator.calculateJaccard(userKeywordsSet, aiKeywordsSet);
        return new FeedbackDto(ActivityType.KEYWORD, String.join(",", userKeywords), String.join(",", aiKeywords), aiFeedback, Double.toString(score));
    }

    // 단어장 관련 OpenAPI 로직, Repository 메서드 필요
    private FeedbackDto getWordsToLearn(List<String> vocabularies) {
        return new FeedbackDto(ActivityType.UNKNOWN_WORD, String.join(",", vocabularies), "", "", "");
    }

    private FeedbackDto checkUserComment(String userComment, String tendency, String aiFeedback) {
        return new FeedbackDto(ActivityType.THOUGHT_SUMMARY, userComment, "", aiFeedback, tendency);
    }

    private Map<String, Object> requestFeedbackByAI(ScrapActivityDto activity, Article article) throws JsonProcessingException {
        PromptTemplate scrapFeedbackSystemPromptTemplate = new PromptTemplate(SCRAP_FEEDBACK_SYSTEM_PROMPT);
        PromptTemplate scrapFeedbackUserPromptTemplate = new PromptTemplate(SCRAP_FEEDBACK_USER_PROMPT);
        Category category = new Category(article.getArticleId());
        Map<String, Object> systemPromptValues = Map.of(
                "title", article.getTitle(),
                "content", article.getContent(),
                "category", category.getCategoryName()
        );
        Map<String, Object> userPromptValues = Map.of(
                "title", activity.getTitle(),
                "summary", activity.getSummary(),
                "category", activity.getCategory(),
                "keywords", activity.getKeywords(),
                "comment", activity.getComment()
        );
        String systemPrompt = scrapFeedbackSystemPromptTemplate.render(systemPromptValues);
        String userPrompt = scrapFeedbackUserPromptTemplate.render(userPromptValues);
        String chatResponse = ChatClient.builder(this.chatModel).build()
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        Map<String, Object> feedbackResult = objectMapper.readValue(
                chatResponse,
                new TypeReference<Map<String, Object>>() {}
        );
        return feedbackResult;
    }

    private Article getArticleHardcoding() {
        String title = "국정기획위, 13일 대국민 보고대회…국정과제·조직개편안 공개";
        String content = """
AI활성화 등 5년 청사진 선보일듯

기재부는 이달 경제성장전략 발표
아동수당 등 반영한 예산도 공개

이달 말까지 이재명 정부의 주요 정책 세부 방안과 이를 실행할 정부 조직개편안이 속속 확정될 전망이다. 새 정부 대통령직인수위원회 역할을 맡은 국정기획위원회는 이번주에 세부 국정과제와 정부 조직개편안을 공개하기로 했다. 기획재정부는 이를 뒷받침할 구체적 경제성장 전략과 내년 예산안을 연이어 발표한다.

10일 관계부처에 따르면 국정기획위는 오는 13일 청와대 영빈관에서 ‘대국민 보고대회’를 열기로 했다. ‘국민주권 정부’의 5년 청사진을 공개하는 자리다. 새 정부 국정과제는 중점 전략과제 12개와 세부과제 123개로 구성된 것으로 알려졌다. 국정기획위 관계자는 “인공지능(AI)산업 활성화, 자본시장 선진화 등 핵심 정책이 담길 것”이라고 말했다.이날 국정기획위는 새 정부 조직개편안도 공개한다. 기재부에서 예산 기능을 떼어내 국무총리실 산하 ‘기획예산처’로 만들고, 남은 기재부는 금융위원회의 국내 금융 정책 기능을 흡수해 ‘재정경제부’로 개편하는 방안이 유력하게 검토되고 있다. 산업통상자원부 에너지정책실을 환경부로 보내 ‘기후환경에너지부’로 확대 개편하거나, 에너지실을 환경부 기후탄소정책실과 합쳐 별도 부처로 구성하는 방안도 최종 결론 날 전망이다. 에너지정책이 산업정책과 분리되는 건 1993년 상공부와 동력자원부가 합쳐져 상공자원부가 만들어진 이후 32년 만에 처음이다.기재부는 이달 하순께 ‘새 정부 경제 성장전략’을 공개하기로 했다. 통상 새 정부 출범 이후 발표하는 ‘새 정부 경제정책 방향’을 성장을 강조하는 ‘성장 전략’으로 바꿨다. 이 대통령 주요 공약인 ‘AI 3대 강국’을 실현하기 위한 AI 제조 로봇 기술과 자율주행 자동차 등 혁신경제 아이템에 투자할 구체적 방안이 담길 것으로 보인다.경제 성장 전략엔 정부 공식 경제성장률 수정 전망치도 담길 것으로 보인다. 정부는 연초 ‘2025년 경제정책 방향’에서 올해 성장률 전망치를 1.8%로 제시했다. 발표 이후 미국발 관세전쟁의 여파로 1분기 한국 경제가 ‘역성장’(직전 분기 대비 -0.2%)을 나타냈고, 최근엔 관세 협상이 타결돼 조정이 불가피해졌다는 분석이다.기재부는 조만간 내년도 정부 예산안도 내놓는다. 이 대통령 대선 공약인 아동수당, 농어촌 기본수당 확대에 필요한 예산을 어느 수준으로 확보할지가 관건이다. 아동수당은 지급 대상을 올해 8세까지에서 내년부터 9세까지로 확대한다면 필요한 재원 규모가 3조8800억원 수준이다.이광식/하지은 기자 bumeran@hankyung.com
""";
        Long categoryId = 6L;
        String publishedAtStr = "2025.8.10  6:26:00 PM";
        String createdAtStr = "2025.8.20  10:00:00 PM";
        // 패턴 정의
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.M.d  h:mm:ss a");
        // 문자열 -> LocalDateTime
        LocalDateTime publishedAt = LocalDateTime.parse(publishedAtStr, formatter);
        LocalDateTime createdAt = LocalDateTime.parse(createdAtStr, formatter);
        String reporter = "이광식 기자,하지은 기자";
        String publisher = "한국경제";
        String articleUrl = "https://www.hankyung.com/article/2025081019061";

        Article article = new Article(1L, 6, title, content, publishedAt, reporter, publisher, articleUrl, createdAt);
        return article;
    }
}
