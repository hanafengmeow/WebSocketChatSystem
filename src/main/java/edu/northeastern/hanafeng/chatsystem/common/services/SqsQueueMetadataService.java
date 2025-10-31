package edu.northeastern.hanafeng.chatsystem.common.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

@Service
@Slf4j
@RequiredArgsConstructor
public class SqsQueueMetadataService {

    private final SqsClient sqsClient;

    @Value("${websocketchat.backend.sqs.queue-name-pattern}")
    private String queueNamePattern;

    @Value("${websocketchat.backend.sqs.dlq-name}")
    private String dlqName;

    public String getChatRoomQueueName(String roomId) {
        return queueNamePattern.replace("{roomId}", roomId);
    }

    @Cacheable(value = "queueUrls", key = "#roomId", unless = "#result == null")
    public String getChatRoomQueueUrl(String roomId) {
        return getQueueUrl(getChatRoomQueueName(roomId));
    }

    @Cacheable(value = "queueUrls", key = "'dlq'", unless = "#result == null")
    public String getChatRoomDlqUrl() {
        return getQueueUrl(dlqName);
    }

    private String getQueueUrl(String queueName) {
        try {
            GetQueueUrlRequest getQueueUrlRequest = GetQueueUrlRequest.builder()
                    .queueName(queueName)
                    .build();

            GetQueueUrlResponse response = sqsClient.getQueueUrl(getQueueUrlRequest);
            log.info("Retrieved and cached queue URL for: {}", queueName);
            return response.queueUrl();

        } catch (QueueDoesNotExistException e) {
            return null;
        }
    }

    @CacheEvict(value = "queueUrls", key = "#roomId")
    public void evictChatRoomQueueUrlFromCache(String roomId) {
        log.info("Evicted queue URL from cache for room: {}", roomId);
    }

    @CacheEvict(value = "queueUrls", key = "'dlq'")
    public void evictChatRoomDlqUrlFromCache() {
        log.info("Evicted DLQ URL from cache");
    }
}
