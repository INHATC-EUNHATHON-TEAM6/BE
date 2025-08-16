package com.words_hanjoom;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
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
