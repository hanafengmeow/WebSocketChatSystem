package edu.northeastern.hanafeng.chatsystem.common.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.hanafeng.chatsystem.common.interfaces.SqsMessageHandler;
import edu.northeastern.hanafeng.chatsystem.model.ChatMessage;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class SqsPollingService {

    private final SqsClient sqsClient;
    private final SqsQueueMetadataService sqsQueueMetadataService;
    private final SqsMessageService sqsMessageService;
    private final ObjectMapper objectMapper;

    @Value("${websocketchat.backend.max-rooms}")
    private int maxRooms;

    @Value("${websocketchat.backend.sqs.wait-time-seconds}")
    private int waitTimeSeconds;

    @Value("${websocketchat.backend.sqs.queue-check-retry-seconds}")
    private int queueCheckRetrySeconds;

    @Value("${websocketchat.backend.sqs.max-messages-per-poll}")
    private int maxMessagesPerPoll;

    @Value("${websocketchat.backend.sqs.max-retries}")
    private int maxRetries;

    private SqsMessageHandler messageHandler;
    private ExecutorService executorService;
    private volatile boolean running = false;

    /**
     * Register a message handler to process messages from SQS.
     *
     * @param handler The message handler implementation
     * @return this for method chaining
     */
    public SqsPollingService registerHandler(SqsMessageHandler handler) {
        if (running) {
            throw new IllegalStateException("Cannot register handler while polling service is running");
        }

        this.messageHandler = handler;
        log.info("Registered message handler: {}", handler.getClass().getSimpleName());
        return this;
    }

    public void start() {
        if (messageHandler == null) {
            throw new IllegalStateException("No message handler registered. Call registerHandler() before start()");
        }

        if (running) {
            log.warn("SQS Polling Service is already running");
            return;
        }

        log.info("Starting SQS Polling Service with one thread per room ({} rooms)", maxRooms);

        // Create one thread per room
        executorService = Executors.newFixedThreadPool(maxRooms);
        running = true;

        // Start one dedicated polling thread for each room
        for (int roomId = 1; roomId <= maxRooms; roomId++) {
            final String roomIdStr = String.valueOf(roomId);
            executorService.submit(() -> pollRoomQueue(roomIdStr));
        }

        log.info("SQS Polling Service started successfully with {} threads", maxRooms);
    }

    @PreDestroy
    public void stop() {
        log.info("Stopping SQS Polling Service");
        running = false;

        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("SQS Polling Service stopped");
    }

    private void pollRoomQueue(String roomId) {
        String queueName = sqsQueueMetadataService.getChatRoomQueueName(roomId);
        log.info("Polling thread started for room: {} (queue: {})", roomId, queueName);

        while (running) {
            try {
                // Get queue URL, wait if it doesn't exist yet
                String queueUrl = sqsQueueMetadataService.getChatRoomQueueUrl(roomId);
                if (queueUrl == null) {
                    // Queue doesn't exist yet, wait and retry
                    log.info("Room {}: Queue {} does not exist yet, waiting {} seconds",
                            roomId, queueName, queueCheckRetrySeconds);
                    if (!sleepUninterruptibly(queueCheckRetrySeconds * 1000L)) {
                        break;
                    }
                    continue;
                }

                // Poll for messages
                List<Message> messages = receiveMessages(queueUrl);
                if (messages.isEmpty()) {
                    log.trace("Room {}: No messages received from queue: {}", roomId, queueName);
                } else {
                    log.info("Room {}: Received {} messages from queue: {}", roomId, messages.size(), queueName);
                    for (Message message : messages) {
                        processMessageWithRetry(roomId, queueUrl, queueName, message);
                    }
                }
            } catch (QueueDoesNotExistException e) {
                log.info("Room {}: Queue {} does not exist, evicting from cache", roomId, queueName);
                sqsQueueMetadataService.evictChatRoomQueueUrlFromCache(roomId);
                if (!sleepUninterruptibly(queueCheckRetrySeconds * 1000L)) {
                    break;
                }

            } catch (Exception e) {
                log.error("Room {}: Error polling queue: {}", roomId, queueName, e);
                if (!sleepUninterruptibly(5000)) {
                    break;
                }
            }
        }

        log.info("Polling thread stopped for room: {}", roomId);
    }

    private void processMessageWithRetry(String roomId, String queueUrl, String queueName, Message message) {
        int attempt = 0;
        Exception lastException = null;
        ChatMessage chatMessage = null;

        // Deserialize message once before retrying
        try {
            chatMessage = objectMapper.readValue(message.body(), ChatMessage.class);
        } catch (Exception e) {
            log.error("Room {}: Failed to deserialize message, sending to DLQ", roomId, e);
            sqsMessageService.sendChatMessageToDlq(roomId, null, e);
            deleteMessage(roomId, message.receiptHandle());
            return;
        }

        while (attempt < maxRetries) {
            try {
                attempt++;
                log.info("Room {}: Processing message (attempt {}/{})", roomId, attempt, maxRetries);

                // Delegate to message handler with deserialized ChatMessage
                messageHandler.handleMessage(roomId, chatMessage);

                // Delete message after successful handling
                deleteMessage(roomId, message.receiptHandle());
                log.info("Room {}: Successfully processed and deleted message", roomId);
                return;

            } catch (Exception e) {
                lastException = e;
                log.warn("Room {}: Failed to process message (attempt {}/{}): {}",
                        roomId, attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    // Exponential backoff before retry
                    long backoffMs = (long) Math.pow(2, attempt - 1) * 1000;
                    if (!sleepUninterruptibly(backoffMs)) {
                        log.error("Room {}: Retry backoff interrupted", roomId);
                        break;
                    }
                }
            }
        }

        // All retries exhausted, send to DLQ
        log.error("Room {}: All {} retry attempts failed for message, sending to DLQ",
                roomId, maxRetries, lastException);
        sqsMessageService.sendChatMessageToDlq(roomId, chatMessage, lastException);

        // Delete the original message to prevent reprocessing
        deleteMessage(roomId, message.receiptHandle());
    }

    private List<Message> receiveMessages(String queueUrl) {
        // Poll for messages with configured wait time (long polling)
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(maxMessagesPerPoll)
                .waitTimeSeconds(waitTimeSeconds)
                .build();

        ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
        return response.messages();
    }

    private void deleteMessage(String roomId, String receiptHandle) {
        try {
            String queueUrl = sqsQueueMetadataService.getChatRoomQueueUrl(roomId);
            if (queueUrl == null) {
                log.error("Cannot delete message: queue for room {} does not exist", roomId);
                return;
            }

            DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(receiptHandle)
                    .build();

            sqsClient.deleteMessage(deleteRequest);
            log.info("Deleted message from queue for room: {}", roomId);

        } catch (Exception e) {
            log.error("Failed to delete message from queue for room: {}", roomId, e);
        }
    }

    /**
     * Sleep for the specified duration, handling interrupts gracefully.
     *
     * @param millis Duration to sleep in milliseconds
     * @return true if sleep completed normally, false if interrupted
     */
    private boolean sleepUninterruptibly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
