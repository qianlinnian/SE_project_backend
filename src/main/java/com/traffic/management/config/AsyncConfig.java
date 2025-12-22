package com.traffic.management.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务配置
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "videoAnalysisExecutor")
    public Executor videoAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数：处理视频分析的基本线程数
        executor.setCorePoolSize(2);

        // 最大线程数：高峰期可扩展到的最大线程数
        executor.setMaxPoolSize(5);

        // 队列容量：等待队列的大小
        executor.setQueueCapacity(10);

        // 线程名前缀
        executor.setThreadNamePrefix("VideoAnalysis-");

        // 拒绝策略：队列满时的处理策略
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        // 等待时间：应用关闭时等待任务完成的时间
        executor.setAwaitTerminationSeconds(60);

        // 优雅关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);

        executor.initialize();
        return executor;
    }
}