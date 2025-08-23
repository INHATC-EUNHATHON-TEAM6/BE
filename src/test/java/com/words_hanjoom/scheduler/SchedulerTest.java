package com.words_hanjoom.scheduler;

import com.words_hanjoom.domain.crawling.scheduler.CrawlScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class SchedulerTest {

    @Autowired
    private CrawlScheduler crawlScheduler;

    @Test
    public void testScheduler() {
        crawlScheduler.runScheduler();
    }
}
