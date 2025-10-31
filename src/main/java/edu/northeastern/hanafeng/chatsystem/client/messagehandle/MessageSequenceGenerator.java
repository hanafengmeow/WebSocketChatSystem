package edu.northeastern.hanafeng.chatsystem.client.messagehandle;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.hanafeng.chatsystem.client.config.ClientConfig;
import edu.northeastern.hanafeng.chatsystem.client.metrics.ClientMetricsAggregator;
import edu.northeastern.hanafeng.chatsystem.client.user.UserHandlerManager;
import edu.northeastern.hanafeng.chatsystem.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Message producer - generates messages in sequence from all users.
 * Supports multiple producer workers via @Async.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageSequenceGenerator {

    private final UserHandlerManager userHandlerManager;
    private final BlockingQueue<String> sharedMessageQueue;
    private final ClientConfig clientConfig;
    private final ObjectMapper objectMapper;
    private final ClientMetricsAggregator metricsAggregator;

    @Value("${client.metrics.batch-size:10000}")
    private int metricBatchSize;

    private final AtomicLong producedCount = new AtomicLong(0);
    private final AtomicBoolean poisonPillsSent = new AtomicBoolean(false);

    public void resetState() {
        producedCount.set(0);
        poisonPillsSent.set(false);
    }

    @Async("messageProducerExecutor")
    public void startProducingAsync() {
        final long totalMessages = clientConfig.getTotalMessages();
        final int numDispatchers = clientConfig.getDispatcherThreads();

        try {
            while (true) {
                long currentProduced = producedCount.get();
                if (currentProduced >= totalMessages) {
                    break;
                }

                ChatMessage message;
                try {
                    message = userHandlerManager.getNextMessage();
                } catch (IllegalStateException ex) {
                    log.trace("Producer {} skipped user: {}", Thread.currentThread().getName(), ex.getMessage());
                    continue;
                }

                if (message == null) {
                    if (producedCount.get() >= totalMessages) {
                        break;
                    }
                    Thread.sleep(2);
                    continue;
                }

                String jsonMessage = objectMapper.writeValueAsString(message);
                sharedMessageQueue.put(jsonMessage);

                long afterIncrement = producedCount.incrementAndGet();

                if (afterIncrement % metricBatchSize == 0 || afterIncrement == totalMessages) {
                    int depth = sharedMessageQueue.size();
                    int totalCapacity = clientConfig.getMessageQueueCapacity();
                    double usagePercent = totalCapacity == 0 ? 0.0 : depth * 100.0 / totalCapacity;

                    log.info("Producer {} progress: {}/{} | queue depth {} / {} ({}%)",
                            Thread.currentThread().getName(),
                            afterIncrement,
                            totalMessages,
                            depth,
                            totalCapacity,
                            String.format("%.2f", usagePercent));

                    metricsAggregator.recordQueueSample(depth, totalCapacity);
                }

                if (afterIncrement >= totalMessages) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Producer interrupted", e);
        } catch (Exception e) {
            log.error("Producer error", e);
        } finally {
            if (poisonPillsSent.compareAndSet(false, true)) {
                try {
                    for (int i = 0; i < numDispatchers; i++) {
                        sharedMessageQueue.put("POISON_PILL");
                    }
                    log.info("Sent {} POISON_PILL signals", numDispatchers);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Failed to enqueue poison pills", e);
                }
            }
        }
    }
}