package edu.northeastern.hanafeng.chatsystem.common.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SqsQueueLifecycleService {

    private final SqsClient sqsClient;
    private final SqsQueueMetadataService sqsQueueMetadataService;

    @Value("${websocketchat.backend.sqs.dlq-name}")
    private String dlqName;

    // Queue creation methods

    public String createChatRoomQueue(String roomId) {
        return createQueue(sqsQueueMetadataService.getChatRoomQueueName(roomId));
    }

    public String createChatRoomDlq() {
        return createQueue(dlqName);
    }

    private String createQueue(String queueName) {
        Map<QueueAttributeName, String> attributes = new HashMap<>();
        attributes.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, "1209600"); // 14 days
        attributes.put(QueueAttributeName.VISIBILITY_TIMEOUT, "30");

        // Add tag for CDK cleanup
        Map<String, String> tags = new HashMap<>();
        // TODO: This should not be hardcoded. Pass them from environment variables and manage with CDK.
        tags.put("ManagedBy", "WebSocketChatSystem");
        tags.put("Application", "ChatRoom");

        CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                .queueName(queueName)
                .attributes(attributes)
                .tags(tags)
                .build();

        CreateQueueResponse createQueueResponse = sqsClient.createQueue(createQueueRequest);
        log.info("Created SQS queue: {} with URL: {}", queueName, createQueueResponse.queueUrl());
        return createQueueResponse.queueUrl();
    }

    /**
     * Delete chat room queue asynchronously
     * This is a cleanup operation that doesn't need to block the caller
     */
    @Async
    @CacheEvict(value = "queueUrls", key = "#roomId")
    public void deleteChatRoomQueue(String roomId) {
        String queueUrl = sqsQueueMetadataService.getChatRoomQueueUrl(roomId);
        if (queueUrl != null) {
            deleteQueue(queueUrl);
            log.info("Deleted chat room queue for room: {}", roomId);
        } else {
            log.warn("Cannot delete queue for room {}: queue does not exist", roomId);
        }
    }

    /**
     * Delete DLQ asynchronously
     * This is a cleanup operation that doesn't need to block the caller
     */
    @Async
    @CacheEvict(value = "queueUrls", key = "'dlq'")
    public void deleteChatRoomDlq() {
        String queueUrl = sqsQueueMetadataService.getChatRoomDlqUrl();
        if (queueUrl != null) {
            deleteQueue(queueUrl);
            log.info("Deleted chat room DLQ");
        } else {
            log.warn("Cannot delete DLQ: queue does not exist");
        }
    }

    private void deleteQueue(String queueUrl) {
        try {
            DeleteQueueRequest deleteQueueRequest = DeleteQueueRequest.builder()
                    .queueUrl(queueUrl)
                    .build();

            sqsClient.deleteQueue(deleteQueueRequest);
            log.info("Deleted queue: {}", queueUrl);

        } catch (Exception e) {
            log.warn("Failed to delete queue: {}", queueUrl, e);
        }
    }
}
