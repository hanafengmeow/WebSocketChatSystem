package edu.northeastern.hanafeng.chatsystem.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Value("${websocketchat.async.core-pool-size}")
    private int corePoolSize;

    @Value("${websocketchat.async.max-pool-size}")
    private int maxPoolSize;

    @Value("${websocketchat.async.queue-capacity}")
    private int queueCapacity;

    @Value("${websocketchat.async.thread-name-prefix}")
    private String threadNamePrefix;

    /**
     * Create and configure the async executor bean
     * This bean is used by @Async methods and by ThreadPoolMetricsService for monitoring
     */
    @Bean(name = "asyncExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);

        // Set rejection policy to handle task overflow gracefully
        // CallerRunsPolicy: The caller thread executes the task when pool is exhausted
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        // Allow core threads to timeout and be reclaimed when idle
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);

        executor.initialize();
        log.info("Async executor configured: corePoolSize={}, maxPoolSize={}, queueCapacity={}, threadNamePrefix={}, rejectionPolicy=CallerRunsPolicy",
                corePoolSize, maxPoolSize, queueCapacity, threadNamePrefix);
        return executor;
    }
}
