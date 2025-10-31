package edu.northeastern.hanafeng.chatsystem.common.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Service for publishing thread pool metrics to CloudWatch
 * Monitors async executor health and performance
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ThreadPoolMetricsService {

    private final Executor asyncExecutor;
    private final CloudWatchMetricsService metricsService;

    @Value("${websocketchat.metrics.thread-pool.interval-ms:60000}")
    private long metricsIntervalMs;

    /**
     * Publish thread pool metrics at configured interval
     * These metrics help monitor async executor health and identify bottlenecks
     * Default interval: 60 seconds
     */
    @Scheduled(fixedRateString = "${websocketchat.metrics.thread-pool.interval-ms:60000}")
    public void publishThreadPoolMetrics() {
        if (!(asyncExecutor instanceof ThreadPoolTaskExecutor)) {
            log.warn("Async executor is not a ThreadPoolTaskExecutor, cannot publish metrics");
            return;
        }

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) asyncExecutor;
        ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();

        try {
            // Active threads - currently executing tasks
            int activeCount = executor.getActiveCount();
            metricsService.recordMetric("ThreadPool.ActiveCount", activeCount);

            // Pool size - current number of threads in the pool
            int poolSize = executor.getPoolSize();
            metricsService.recordMetric("ThreadPool.PoolSize", poolSize);

            // Queue size - tasks waiting to be executed
            int queueSize = threadPoolExecutor.getQueue().size();
            metricsService.recordMetric("ThreadPool.QueueSize", queueSize);

            // Completed task count - total tasks completed since startup
            long completedTaskCount = threadPoolExecutor.getCompletedTaskCount();
            metricsService.recordMetric("ThreadPool.CompletedTaskCount", completedTaskCount);

            // Core pool size - minimum threads kept alive
            int corePoolSize = executor.getCorePoolSize();
            metricsService.recordMetric("ThreadPool.CorePoolSize", corePoolSize);

            // Max pool size - maximum threads allowed
            int maxPoolSize = executor.getMaxPoolSize();
            metricsService.recordMetric("ThreadPool.MaxPoolSize", maxPoolSize);

            // Utilization percentage - active threads / pool size
            double utilization = poolSize > 0 ? (double) activeCount / poolSize * 100 : 0;
            metricsService.recordMetric("ThreadPool.Utilization", utilization);

            // Queue utilization - queue size / queue capacity
            int queueCapacity = threadPoolExecutor.getQueue().remainingCapacity() + queueSize;
            double queueUtilization = queueCapacity > 0 ? (double) queueSize / queueCapacity * 100 : 0;
            metricsService.recordMetric("ThreadPool.QueueUtilization", queueUtilization);

            // Largest pool size reached
            int largestPoolSize = threadPoolExecutor.getLargestPoolSize();
            metricsService.recordMetric("ThreadPool.LargestPoolSize", largestPoolSize);

            log.debug("Published thread pool metrics: active={}, pool={}, queue={}, utilization={}%, queueUtil={}%",
                    activeCount, poolSize, queueSize, String.format("%.1f", utilization), String.format("%.1f", queueUtilization));

            // Log warning if thread pool is saturated
            if (utilization > 80 && queueUtilization > 50) {
                log.warn("Thread pool is saturated: active={}/{}, queue={}/{} ({}% full)",
                        activeCount, poolSize, queueSize, queueCapacity, String.format("%.1f", queueUtilization));
            }

        } catch (Exception e) {
            log.error("Failed to publish thread pool metrics: {}", e.getMessage(), e);
        }
    }

    /**
     * Get current thread pool statistics for debugging
     */
    public String getThreadPoolStats() {
        if (!(asyncExecutor instanceof ThreadPoolTaskExecutor)) {
            return "Executor is not a ThreadPoolTaskExecutor";
        }

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) asyncExecutor;
        ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();

        return String.format(
                "ThreadPool[active=%d, pool=%d/%d, queue=%d/%d, completed=%d, largest=%d]",
                executor.getActiveCount(),
                executor.getPoolSize(),
                executor.getMaxPoolSize(),
                threadPoolExecutor.getQueue().size(),
                threadPoolExecutor.getQueue().remainingCapacity() + threadPoolExecutor.getQueue().size(),
                threadPoolExecutor.getCompletedTaskCount(),
                threadPoolExecutor.getLargestPoolSize()
        );
    }
}
