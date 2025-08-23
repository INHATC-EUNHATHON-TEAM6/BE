package com.words_hanjoom;

import io.github.cdimascio.dotenv.Dotenv;
import com.words_hanjoom.domain.crawling.scheduler.CrawlScheduler;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableJpaRepositories
@SpringBootApplication(exclude = SecurityAutoConfiguration.class)
public class WordsHanjoomApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure()
                .directory("./")  // .env 파일 위치 (프로젝트 루트)
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();

        // 환경 변수로 시스템 프로퍼티 설정
        dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue())
        );
        SpringApplication.run(WordsHanjoomApplication.class, args);
    }
}

