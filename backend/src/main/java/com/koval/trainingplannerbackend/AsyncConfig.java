package com.koval.trainingplannerbackend;

import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounded executor for {@code @Async} methods (audit log, notifications, integration push).
 * Spring's default {@code SimpleAsyncTaskExecutor} spawns an unbounded number of threads,
 * which under load drives heap pressure. CallerRunsPolicy applies backpressure at the
 * call site if the queue saturates.
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Value("${async.executor.core-size:8}")
    private int coreSize;

    @Value("${async.executor.max-size:32}")
    private int maxSize;

    @Value("${async.executor.queue-capacity:500}")
    private int queueCapacity;

    @Value("${async.executor.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Override
    public org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }

    /**
     * Dedicated pool for SSE fan-out so a slow consumer can't starve other
     * {@code @Async} work, and one club's broadcast can't block another's.
     * Per-send timeouts plus this pool's bounded queue cap the worst case.
     */
    @Bean(name = "sseExecutor")
    public Executor sseExecutor(
            @Value("${async.sse-executor.core-size:4}") int coreSize,
            @Value("${async.sse-executor.max-size:16}") int maxSize,
            @Value("${async.sse-executor.queue-capacity:1000}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("sse-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }
}
