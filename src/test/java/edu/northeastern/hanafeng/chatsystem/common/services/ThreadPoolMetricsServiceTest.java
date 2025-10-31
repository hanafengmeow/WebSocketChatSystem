package edu.northeastern.hanafeng.chatsystem.common.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThreadPoolMetricsServiceTest {

    @Mock
    private CloudWatchMetricsService metricsService;

    @Captor
    private ArgumentCaptor<String> metricNameCaptor;

    @Captor
    private ArgumentCaptor<Double> metricValueCaptor;

    private ThreadPoolMetricsService threadPoolMetricsService;
    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void setUp() {
        // Create a real ThreadPoolTaskExecutor for testing
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(40);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("test-async-");
        executor.initialize();

        threadPoolMetricsService = new ThreadPoolMetricsService(executor, metricsService);
    }

    @Test
    void testPublishThreadPoolMetrics_Success() {
        // When
        threadPoolMetricsService.publishThreadPoolMetrics();

        // Then - verify all expected metrics are published
        verify(metricsService, atLeastOnce()).recordMetric(metricNameCaptor.capture(), metricValueCaptor.capture());

        var metricNames = metricNameCaptor.getAllValues();
        assertTrue(metricNames.contains("ThreadPool.ActiveCount"));
        assertTrue(metricNames.contains("ThreadPool.PoolSize"));
        assertTrue(metricNames.contains("ThreadPool.QueueSize"));
        assertTrue(metricNames.contains("ThreadPool.CompletedTaskCount"));
        assertTrue(metricNames.contains("ThreadPool.CorePoolSize"));
        assertTrue(metricNames.contains("ThreadPool.MaxPoolSize"));
        assertTrue(metricNames.contains("ThreadPool.Utilization"));
        assertTrue(metricNames.contains("ThreadPool.QueueUtilization"));
        assertTrue(metricNames.contains("ThreadPool.LargestPoolSize"));
    }

    @Test
    void testPublishThreadPoolMetrics_CorrectCorePoolSize() {
        // When
        threadPoolMetricsService.publishThreadPoolMetrics();

        // Then
        verify(metricsService).recordMetric(eq("ThreadPool.CorePoolSize"), eq(10.0));
    }

    @Test
    void testPublishThreadPoolMetrics_CorrectMaxPoolSize() {
        // When
        threadPoolMetricsService.publishThreadPoolMetrics();

        // Then
        verify(metricsService).recordMetric(eq("ThreadPool.MaxPoolSize"), eq(40.0));
    }

    @Test
    void testPublishThreadPoolMetrics_WithNonThreadPoolExecutor() {
        // Given - create service with a simple Executor
        Executor simpleExecutor = Runnable::run;
        ThreadPoolMetricsService service = new ThreadPoolMetricsService(simpleExecutor, metricsService);

        // When
        service.publishThreadPoolMetrics();

        // Then - no metrics should be published
        verify(metricsService, never()).recordMetric(anyString(), anyDouble());
    }

    @Test
    void testPublishThreadPoolMetrics_WithException() {
        // Given
        doThrow(new RuntimeException("CloudWatch error"))
                .when(metricsService).recordMetric(anyString(), anyDouble());

        // When - should not throw exception
        assertDoesNotThrow(() -> threadPoolMetricsService.publishThreadPoolMetrics());
    }

    @Test
    void testGetThreadPoolStats_Success() {
        // When
        String stats = threadPoolMetricsService.getThreadPoolStats();

        // Then
        assertNotNull(stats);
        assertTrue(stats.contains("ThreadPool"));
        assertTrue(stats.contains("active="));
        assertTrue(stats.contains("pool="));
        assertTrue(stats.contains("queue="));
        assertTrue(stats.contains("completed="));
        assertTrue(stats.contains("largest="));
    }

    @Test
    void testGetThreadPoolStats_WithNonThreadPoolExecutor() {
        // Given
        Executor simpleExecutor = Runnable::run;
        ThreadPoolMetricsService service = new ThreadPoolMetricsService(simpleExecutor, metricsService);

        // When
        String stats = service.getThreadPoolStats();

        // Then
        assertEquals("Executor is not a ThreadPoolTaskExecutor", stats);
    }

    @Test
    void testUtilizationCalculation_WithActiveThreads() {
        // Given - submit some tasks to create active threads
        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Wait a bit for threads to start
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When
        threadPoolMetricsService.publishThreadPoolMetrics();

        // Then - utilization should be recorded
        verify(metricsService).recordMetric(eq("ThreadPool.Utilization"), anyDouble());
    }
}
