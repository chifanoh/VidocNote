package com.vidoc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 摘要任务的异步线程池。池容量就是在途 LLM 调用数的硬上限，
 * 必须低于模型在账号上的并发额度 —— 超了只会制造 429 退避并把线程全占住。
 */
@Configuration
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /** 排队位。等超过这个长度用户早走了，不如早点失败（503）让前端提示 */
    private static final int QUEUE_CAPACITY = 100;

    @Bean(name = "summaryExecutor")
    public ThreadPoolTaskExecutor summaryExecutor(
            @Value("${vidoc.summary.concurrency:8}") int concurrency) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // core 必须等于 max：ThreadPoolExecutor 只在队列满时才把线程数从 core 扩到 max，
        // core 小于 max 等于把真实并发压在 core 上（队列 100 要排满才会用到后面的线程）
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("summary-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("摘要并发上限：{}（队列 {}，超出报 503）", concurrency, QUEUE_CAPACITY);
        return executor;
    }
}
