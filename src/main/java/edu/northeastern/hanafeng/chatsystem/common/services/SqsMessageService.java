package edu.northeastern.hanafeng.chatsystem.common.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.hanafeng.chatsystem.model.ChatMessage;
import edu.northeastern.hanafeng.chatsystem.model.DLQMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class SqsMessageService {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final SqsQueueMetadataService sqsQueueMetadataService;
    private final SqsQueueLifecycleService sqsQueueLifecycleService;

    @Value("${websocketchat.backend.sqs.dlq-name}")
    private String dlqName;

    @Async
    public void sendChatMessageToQueue(ChatMessage chatMessage, String roomId) {
        try {
            String queueUrl = getOrCreateChatRoomQueueUrl(roomId);
            String messageBody = objectMapper.writeValueAsString(chatMessage);
            sendMessage(queueUrl, messageBody);
            log.info("Sent message to SQS queue for room: {}", roomId);
        } catch (Exception e) {
            log.error("Failed to send message to SQS queue for room: {}", roomId, e);
        }
    }

    @Async
    public void sendChatMessageToDlq(String roomId, ChatMessage chatMessage, Exception error) {
        try {
            String dlqUrl = getOrCreateDlqUrl();

            // Create DLQ message with metadata
            DLQMessage dlqMessage = new DLQMessage();
            dlqMessage.setRoomId(roomId);
            dlqMessage.setOriginalMessage(chatMessage);
            dlqMessage.setError(error.getMessage());
            dlqMessage.setTimestamp(Instant.now());

            // Serialize to JSON
            String dlqMessageBody = objectMapper.writeValueAsString(dlqMessage);
            sendMessage(dlqUrl, dlqMessageBody);
            log.info("Room {}: Sent failed message to DLQ: {}", roomId, dlqName);

        } catch (Exception e) {
            log.error("Room {}: Failed to send message to DLQ: {}", roomId, dlqName, e);
        }
    }

    private String getOrCreateChatRoomQueueUrl(String roomId) {
        String queueUrl = sqsQueueMetadataService.getChatRoomQueueUrl(roomId);
        if (queueUrl == null) {
            log.info("Queue for room {} does not exist, creating it", roomId);
            queueUrl = sqsQueueLifecycleService.createChatRoomQueue(roomId);
        }
        return queueUrl;
    }

    private String getOrCreateDlqUrl() {
        String dlqUrl = sqsQueueMetadataService.getChatRoomDlqUrl();
        if (dlqUrl == null) {
            log.info("DLQ {} does not exist, creating it", dlqName);
            dlqUrl = sqsQueueLifecycleService.createChatRoomDlq();
        }
        return dlqUrl;
    }

    private void sendMessage(String queueUrl, String messageBody) {
        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build();

        sqsClient.sendMessage(sendMessageRequest);
    }
}
