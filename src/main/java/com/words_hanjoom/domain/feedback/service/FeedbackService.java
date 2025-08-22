package com.words_hanjoom.domain.feedback.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.words_hanjoom.domain.feedback.dto.request.ScrapActivityDto;
import com.words_hanjoom.domain.feedback.dto.response.FeedbackDto;
import com.words_hanjoom.domain.feedback.dto.response.FeedbackThisMonthActivityDto;
import com.words_hanjoom.domain.feedback.dto.response.FeedbacksDto;
import com.words_hanjoom.domain.feedback.entity.ActivityType;
import com.words_hanjoom.domain.crawling.entity.Article;
import com.words_hanjoom.domain.feedback.event.ScrapActivitySavedEvent;
import com.words_hanjoom.domain.users.entity.Category;
import com.words_hanjoom.domain.feedback.entity.ScrapActivities;
import com.words_hanjoom.domain.feedback.repository.FeedbackRepository;
import com.words_hanjoom.domain.crawling.repository.ArticleRepository;
import com.words_hanjoom.domain.feedback.repository.SpringDataJpaFeedbackRepository;
import com.words_hanjoom.domain.users.entity.User;
import com.words_hanjoom.domain.users.repository.CategoryRepository;
import com.words_hanjoom.domain.users.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

import static com.words_hanjoom.domain.feedback.service.FeedbackServicePromptTemplate.*;

@Service
@Transactional
public class FeedbackService {
    private final UserRepository userRepository;
    private final ArticleRepository articleRepository;
    private final FeedbackRepository feedbackRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;
    private final OpenAiChatModel chatModel;
    private final OpenAiEmbeddingModel embeddingModel;

    // 이벤트 퍼블리셔
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public FeedbackService(UserRepository userRepository,
                           ArticleRepository articleRepository,
                           SpringDataJpaFeedbackRepository feedbackRepository,
                           CategoryRepository categoryRepository,
                           ObjectMapper objectMapper,
                           OpenAiChatModel chatModel,
                           OpenAiEmbeddingModel embeddingModel,
                           ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.articleRepository = articleRepository;
        this.feedbackRepository = feedbackRepository;
        this.categoryRepository = categoryRepository;
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.eventPublisher = eventPublisher;
    }

    public Map<String, List<FeedbackThisMonthActivityDto>> getUserActivitiesThisMonth(String loginId, int year, int month, int day) {
        LocalDateTime startDate = LocalDateTime.of(year, month, 1, 0, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(year, month, day, 23, 59, 59);
        Optional<User> optionalUser = userRepository.findByLoginId(loginId);
        User user = optionalUser.orElseThrow(
                () -> new IllegalArgumentException("해당 유저 없음" + loginId)
        );
        List<FeedbackThisMonthActivityDto> feedbackListThisMonth = feedbackRepository.findByUserIdAndYearAndMonthAndDay(user.getUserId(), startDate, endDate);
        Map<String, List<FeedbackThisMonthActivityDto>> activityOfDay = new HashMap<>();
        for (FeedbackThisMonthActivityDto feedbackRecord : feedbackListThisMonth) {
            Optional<Article> optionalArticle = articleRepository.findById(feedbackRecord.getArticleId());
            Article article = optionalArticle.orElseThrow(
                    () -> new IllegalArgumentException("해당 Article 없음")
            );
            Optional<Category> optionalCategory = categoryRepository.findById(article.getCategoryId());
            Category category = optionalCategory.orElseThrow(
                    () -> new IllegalArgumentException("해당 Category 없음")
            );
            feedbackRecord.setCategory(category.getCategoryName());
            int daily = feedbackRecord.getActivityAt().getDayOfMonth();
            if (activityOfDay.get(Integer.toString(daily)) == null) {
                activityOfDay.put(Integer.toString(daily), new ArrayList<>());
            }
            activityOfDay.get(Integer.toString(daily)).add(feedbackRecord);
        }
        return activityOfDay;
    }

    public FeedbacksDto getScrapActivityRecord(String loginId, long articleId) {
        Optional<User> optionalUser = userRepository.findByLoginId(loginId);
        User user = optionalUser.orElseThrow(
                () -> new IllegalArgumentException("해당 유저 없음" + loginId)
        );
        List<ScrapActivities> scrapActivities = feedbackRepository.findByUserIdAndArticleId(user.getUserId(), articleId);
        Optional<Article> optionalArticle = articleRepository.findById(articleId);
        Article article = optionalArticle.orElseThrow(
                () -> new IllegalArgumentException("해당 Article 없음")
        );
        Optional<Category> optionalCategory = categoryRepository.findById(article.getCategoryId());
        Category category = optionalCategory.orElseThrow(
                () -> new IllegalArgumentException("해당 Category 없음")
        );
        List<FeedbackDto> feedbacks = new ArrayList<>();
        for (ScrapActivities activities : scrapActivities) {
            FeedbackDto feedback = new FeedbackDto(
                    activities.getComparisonType(),
                    activities.getUserAnswer(),
                    activities.getAiAnswer(),
                    activities.getAiFeedback(),
                    activities.getEvaluationScore()
            );
            feedbacks.add(feedback);
        }
        return new FeedbacksDto(article.getContent(), category.getCategoryName(), feedbacks);
    }

    public FeedbacksDto feedbackScrapActivity(String loginId, ScrapActivityDto activity) throws JsonProcessingException {
        Optional<User> optionalUser = userRepository.findByLoginId(loginId);
        User user = optionalUser.orElseThrow(
                () -> new IllegalArgumentException("해당 유저 없음" + loginId)
        );
        Optional<Article> optionalArticle = articleRepository.findById(activity.getArticleId());
        Article article = optionalArticle.orElseThrow(
                () -> new IllegalArgumentException("해당 Article 없음")
        );
        List<FeedbackDto> feedbacks = new ArrayList<>();
        Optional<Category> optionalCategory = categoryRepository.findById(article.getCategoryId());
        Category category = optionalCategory.orElseThrow(
                () -> new IllegalArgumentException("해당 Category 없음")
        );
        Map<String, Object> firstRequest = requestFeedbackByAI(
                Map.of(
                        "title", article.getTitle(),
                        "category", category.getCategoryName(),
                        "content", article.getContent()
                ),
                Map.of(
                        "keywords", activity.getKeywords(),
                        "title", activity.getTitle(),
                        "category", activity.getCategory(),
                        "summary", activity.getSummary()
                ),
                FIRST_SCRAP_FEEDBACK_SYSTEM_PROMPT,
                FIRST_SCRAP_FEEDBACK_USER_PROMPT
        );
        Map<String, Object> secondRequest = requestFeedbackByAI(
                Map.of(
                        "title", article.getTitle(),
                        "category", category.getCategoryName(),
                        "content", article.getContent()
                ),
                Map.of(
                        "comment", activity.getComment()
                ),
                SECOND_SCRAP_FEEDBACK_SYSTEM_PROMPT,
                SECOND_SCRAP_FEEDBACK_USER_PROMPT
        );
        Map<String, Object> aiFeedbacks = (Map<String, Object>)firstRequest.get("feedbacks");
        feedbacks.add(compareCategory(
                activity.getCategory(),
                (String)firstRequest.get("category"),
                (String)aiFeedbacks.get("category"))
        );
        feedbacks.add(compareTitle(
                activity.getTitle(),
                (String)firstRequest.get("title"),
                (String)aiFeedbacks.get("title"))
        );
        feedbacks.add(compareKeywords(
                activity.getKeywords(),
                (List<String>)firstRequest.get("keywords"),
                (String)aiFeedbacks.get("keywords"))
        );
        feedbacks.add(getWordsToLearn(activity.getVocabularies()));
        feedbacks.add(compareSummary(
                activity.getSummary(),
                (String)firstRequest.get("summary"),
                (String)aiFeedbacks.get("summary"))
        );
        feedbacks.add(checkUserComment(
                activity.getComment(),
                (String)secondRequest.get("tendency"),
                (String)secondRequest.get("feedback"))
        );
        for (FeedbackDto feedback : feedbacks) {
            ScrapActivities scrapActivities = new ScrapActivities();
            scrapActivities.setUserId(user.getUserId());
            scrapActivities.setArticleId(article.getArticleId());
            scrapActivities.setComparisonType(feedback.getActivityType());
            scrapActivities.setUserAnswer(feedback.getUserAnswer());
            scrapActivities.setAiAnswer(feedback.getAiAnswer());
            scrapActivities.setAiFeedback(feedback.getAiFeedback());
            scrapActivities.setEvaluationScore(feedback.getEvaluationScore());
            feedbackRepository.save(scrapActivities);

            // 단어 자동수집 트리거: UNKNOWN_WORD인 경우에만 이벤트 발행
            if (scrapActivities.getComparisonType() == ActivityType.UNKNOWN_WORD) {
                eventPublisher.publishEvent(new ScrapActivitySavedEvent(
                        scrapActivities.getUserId(),
                        scrapActivities.getArticleId(),
                        scrapActivities.getComparisonType(),
                        scrapActivities.getUserAnswer()
                ));
            }
        }
        return new FeedbacksDto(article.getContent(), category.getCategoryName(), feedbacks);
    }

    private FeedbackDto compareCategory(String userCategory, String aiCategory, String aiFeedback) {
        EmbeddingResponse userCategoryEmbeddingResponse = embeddingModel.embedForResponse(List.of(userCategory));
        EmbeddingResponse aiCategoryEmbeddingResponse = embeddingModel.embedForResponse(List.of(aiCategory));
        float[] userCategoryVector = userCategoryEmbeddingResponse.getResults().get(0).getOutput();
        float[] aiCategoryVector = aiCategoryEmbeddingResponse.getResults().get(0).getOutput();
        double score = (CosineSimilarity.calculate(userCategoryVector, aiCategoryVector) + 1) / 2.0;
        return new FeedbackDto(ActivityType.CATEGORY, userCategory, aiCategory, aiFeedback, Double.toString(score));
    }

    private FeedbackDto compareTitle(String userTitle, String aiTitle, String aiFeedback) {
        EmbeddingResponse userTitleEmbeddingResponse = embeddingModel.embedForResponse(List.of(userTitle));
        EmbeddingResponse aiTitleEmbeddingResponse = embeddingModel.embedForResponse(List.of(aiTitle));
        float[] userTitleVector = userTitleEmbeddingResponse.getResults().get(0).getOutput();
        float[] aiTitleVector = aiTitleEmbeddingResponse.getResults().get(0).getOutput();
        double score = (CosineSimilarity.calculate(userTitleVector, aiTitleVector) + 1) / 2.0;
        return new FeedbackDto(ActivityType.TITLE, userTitle, aiTitle, aiFeedback, Double.toString(score));
    }

    private FeedbackDto compareKeywords(List<String> userKeywords, List<String> aiKeywords, String aiFeedback) {
        Set<String> userKeywordsSet = new HashSet<>(userKeywords);
        Set<String> aiKeywordsSet = new HashSet<>(aiKeywords);
        double score = SetSimilarityCalculator.calculateJaccard(userKeywordsSet, aiKeywordsSet);
        return new FeedbackDto(ActivityType.KEYWORD, String.join(",", userKeywords), String.join(",", aiKeywords), aiFeedback, Double.toString(score));
    }

    // ❗️하드코딩!
    // 단어장 관련 OpenAPI 로직, Repository 메서드 필요
    // OpenAI API를 통해 국립국어원 API로 검색 안 되는 어휘들을 처리하는 로직은 추후에 적용
    private FeedbackDto getWordsToLearn(List<String> vocabularies) {
        return new FeedbackDto(ActivityType.UNKNOWN_WORD, String.join(",", vocabularies), "", "", "");
    }

    private FeedbackDto compareSummary(String userSummary, String aiSummary, String aiFeedback) {
        double score = RougeCalculator.calculateRouge2(userSummary, aiSummary).getFMeasure();
        return new FeedbackDto(ActivityType.SUMMARY, userSummary, aiSummary, aiFeedback, Double.toString(score));
    }

    private FeedbackDto checkUserComment(String userComment, String tendency, String aiFeedback) {
        return new FeedbackDto(ActivityType.THOUGHT_SUMMARY, userComment, "", aiFeedback, tendency);
    }

    private Map<String, Object> requestFeedbackByAI(
            Map<String, Object> systemPromptValues,
            Map<String, Object> userPromptValues,
            String systemPromptTemplate,
            String userPromptTemplate) throws JsonProcessingException {
        PromptTemplate scrapFeedbackSystemPromptTemplate = new PromptTemplate(systemPromptTemplate);
        PromptTemplate scrapFeedbackUserPromptTemplate = new PromptTemplate(userPromptTemplate);
        String systemPrompt = scrapFeedbackSystemPromptTemplate.render(systemPromptValues);
        String userPrompt = scrapFeedbackUserPromptTemplate.render(userPromptValues);
        String chatResponse = ChatClient.builder(this.chatModel).build()
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        return objectMapper.readValue(
                chatResponse,
                new TypeReference<>() {
                }
        );
    }
}
