package edu.northeastern.hanafeng.chatsystem.client.metrics;

import edu.northeastern.hanafeng.chatsystem.common.services.CloudWatchMetricsService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregates client-side metrics and publishes them to CloudWatch
 * on a fixed schedule to avoid bursty PutMetricData requests.
 *
 * Queue depth/usage metrics are temporarily disabled (not published).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientMetricsAggregator {

    private final CloudWatchMetricsService cloudWatchMetricsService;

    @Value("${client.metrics.publish-interval-ms:5000}")
    private long publishIntervalMs;

    @Value("${client.metrics.initial-delay-ms:5000}")
    private long initialDelayMs;

    // Counters
    private final AtomicLong sentDelta = new AtomicLong();
    private final AtomicLong failedDelta = new AtomicLong();
    private final AtomicLong connectionFailureDelta = new AtomicLong();

    @PostConstruct
    public void logConfiguration() {
        log.info("Client metrics aggregator configured: initialDelay={}ms, interval={}ms", initialDelayMs, publishIntervalMs);
    }

    public void incrementMessagesSent() {
        sentDelta.incrementAndGet();
    }

    public void incrementMessagesFailed() {
        failedDelta.incrementAndGet();
    }

    public void incrementConnectionFailures() {
        connectionFailureDelta.incrementAndGet();
    }

    /**
     * No-op for now: we keep the API so callers don't change,
     * but we don't publish queue depth/usage to CloudWatch.
     */
    public void recordQueueSample(int depth, int capacity) {
        // intentionally no-op
    }

    @Scheduled(
        initialDelayString = "${client.metrics.initial-delay-ms:5000}",
        fixedDelayString   = "${client.metrics.publish-interval-ms:5000}"
    )
    public void publishPeriodically() {
        publish(false);
    }

    public void flush() {
        publish(true);
    }

    private void publish(boolean force) {
        try {
            long sent = sentDelta.getAndSet(0);
            if (sent > 0) {
                cloudWatchMetricsService.recordMetric("ClientMessageSent", sent);
            }

            long failed = failedDelta.getAndSet(0);
            if (failed > 0) {
                cloudWatchMetricsService.recordMetric("ClientMessageFailed", failed);
            }

            long connectionFailures = connectionFailureDelta.getAndSet(0);
            if (connectionFailures > 0) {
                cloudWatchMetricsService.recordMetric("ClientConnectionFailure", connectionFailures);
            }

            // Queue metrics are disabled intentionally:
            // - ClientMessageQueueDepth
            // - ClientMessageQueueUsagePercent
        } catch (Exception e) {
            log.error("Failed to publish aggregated client metrics", e);
        }
    }

    @PreDestroy
    public void onShutdown() {
        publish(true);
    }
}