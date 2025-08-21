package com.words_hanjoom;

import com.words_hanjoom.domain.crawling.scheduler.CrawlScheduler;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(exclude = SecurityAutoConfiguration.class)
public class WordsHanjoomApplication {

    public static void main(String[] args) {
        SpringApplication.run(WordsHanjoomApplication.class, args);
    }

    // CommandLineRunner 빈을 등록하여 애플리케이션 시작 시 실행
    @Bean
    public CommandLineRunner runCrawlOnStartup(CrawlScheduler scheduler) {
        return args -> {
            System.out.println("애플리케이션 시작! 크롤링 작업을 시작합니다.");
            scheduler.runScheduler(); // 스케줄러의 크롤링 메서드 호출
            System.out.println("크롤링 작업 완료.");
        };
    }
}