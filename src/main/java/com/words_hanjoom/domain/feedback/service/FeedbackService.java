package com.words_hanjoom.domain.feedback.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.words_hanjoom.domain.feedback.dto.response.FeedbackDto;
import com.words_hanjoom.domain.feedback.repository.FeedbackRepository;
import jakarta.transaction.Transactional;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.words_hanjoom.domain.feedback.service.FeedbackServicePromptTemplate.SCRAP_FEEDBACK_SYSTEM_PROMPT;
import static com.words_hanjoom.domain.feedback.service.FeedbackServicePromptTemplate.SCRAP_FEEDBACK_USER_PROMPT;

@Service
@Transactional
public class FeedbackService {
    private final FeedbackRepository feedbackRepository;
    private final OpenAiChatModel chatModel;
    private final ObjectMapper objectMapper;


    @Autowired
    public FeedbackService(FeedbackRepository feedbackRepository,
                           ObjectMapper objectMapper,
                           OpenAiChatModel chatModel) {
        this.feedbackRepository = feedbackRepository;
        this.objectMapper = objectMapper;
        this.chatModel = chatModel.mutate()
                .defaultOptions(OpenAiChatOptions.builder().model("gpt-4.1-mini").build())
                .build();
    }

    public FeedbackDto feedbackScrapActivity(String title,
                                             String summary,
                                             String category,
                                             List<String> keywords,
                                             List<String> vocabularies,
                                             String comment) throws JsonProcessingException {
        FeedbackArticle article = getArticleHardcoding();
        PromptTemplate systemPromptTemplate = new PromptTemplate(SCRAP_FEEDBACK_SYSTEM_PROMPT);
        PromptTemplate userPromptTemplate = new PromptTemplate(SCRAP_FEEDBACK_USER_PROMPT);
        Map<String, Object> systemPromptValues = Map.of(
                "title", article.getTitle(),
                "body", article.getBody(),
                "category", article.getCategory()
        );
        Map<String, Object> userPromptValues = Map.of(
                "title", title,
                "summary", summary,
                "category", category,
                "keywords", keywords,
                "comment", comment
        );
        String systemPrompt = systemPromptTemplate.render(systemPromptValues);
        String userPrompt = userPromptTemplate.render(userPromptValues);
        String chatResponse = ChatClient.builder(this.chatModel).build()
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        Map<String, Object> dataMap = objectMapper.readValue(
                chatResponse,
                new TypeReference<Map<String, Object>>() {}
        );
        Map<String, Number> scores = new HashMap<>();
        scores.put("title", 0);
        scores.put("summary", 0);
        scores.put("category", 0);
        scores.put("keywords", 0);
        scores.put("vocabularies", 0);
        scores.put("comment", 0);
        FeedbackDto response = new FeedbackDto(
                title,
                dataMap.get("title").toString(),
                summary,
                dataMap.get("summary").toString(),
                category,
                dataMap.get("category").toString(),
                keywords,
                (List<String>) dataMap.get("keywords"),
                vocabularies,
                comment,
                scores,
                (Map<String, String>) dataMap.get("feedbacks")
        );
        return response;
    }

    private FeedbackArticle getArticleHardcoding() {
        String title = "국정기획위, 13일 대국민 보고대회…국정과제·조직개편안 공개";
        String body = """
AI활성화 등 5년 청사진 선보일듯

기재부는 이달 경제성장전략 발표
아동수당 등 반영한 예산도 공개

이달 말까지 이재명 정부의 주요 정책 세부 방안과 이를 실행할 정부 조직개편안이 속속 확정될 전망이다. 새 정부 대통령직인수위원회 역할을 맡은 국정기획위원회는 이번주에 세부 국정과제와 정부 조직개편안을 공개하기로 했다. 기획재정부는 이를 뒷받침할 구체적 경제성장 전략과 내년 예산안을 연이어 발표한다.

10일 관계부처에 따르면 국정기획위는 오는 13일 청와대 영빈관에서 ‘대국민 보고대회’를 열기로 했다. ‘국민주권 정부’의 5년 청사진을 공개하는 자리다. 새 정부 국정과제는 중점 전략과제 12개와 세부과제 123개로 구성된 것으로 알려졌다. 국정기획위 관계자는 “인공지능(AI)산업 활성화, 자본시장 선진화 등 핵심 정책이 담길 것”이라고 말했다.이날 국정기획위는 새 정부 조직개편안도 공개한다. 기재부에서 예산 기능을 떼어내 국무총리실 산하 ‘기획예산처’로 만들고, 남은 기재부는 금융위원회의 국내 금융 정책 기능을 흡수해 ‘재정경제부’로 개편하는 방안이 유력하게 검토되고 있다. 산업통상자원부 에너지정책실을 환경부로 보내 ‘기후환경에너지부’로 확대 개편하거나, 에너지실을 환경부 기후탄소정책실과 합쳐 별도 부처로 구성하는 방안도 최종 결론 날 전망이다. 에너지정책이 산업정책과 분리되는 건 1993년 상공부와 동력자원부가 합쳐져 상공자원부가 만들어진 이후 32년 만에 처음이다.기재부는 이달 하순께 ‘새 정부 경제 성장전략’을 공개하기로 했다. 통상 새 정부 출범 이후 발표하는 ‘새 정부 경제정책 방향’을 성장을 강조하는 ‘성장 전략’으로 바꿨다. 이 대통령 주요 공약인 ‘AI 3대 강국’을 실현하기 위한 AI 제조 로봇 기술과 자율주행 자동차 등 혁신경제 아이템에 투자할 구체적 방안이 담길 것으로 보인다.경제 성장 전략엔 정부 공식 경제성장률 수정 전망치도 담길 것으로 보인다. 정부는 연초 ‘2025년 경제정책 방향’에서 올해 성장률 전망치를 1.8%로 제시했다. 발표 이후 미국발 관세전쟁의 여파로 1분기 한국 경제가 ‘역성장’(직전 분기 대비 -0.2%)을 나타냈고, 최근엔 관세 협상이 타결돼 조정이 불가피해졌다는 분석이다.기재부는 조만간 내년도 정부 예산안도 내놓는다. 이 대통령 대선 공약인 아동수당, 농어촌 기본수당 확대에 필요한 예산을 어느 수준으로 확보할지가 관건이다. 아동수당은 지급 대상을 올해 8세까지에서 내년부터 9세까지로 확대한다면 필요한 재원 규모가 3조8800억원 수준이다.이광식/하지은 기자 bumeran@hankyung.com
""";
        String category = "경제";
        FeedbackArticle article = new FeedbackArticle(title, body, category);
        return article;
    }
}
