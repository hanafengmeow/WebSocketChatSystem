package edu.northeastern.hanafeng.chatsystem.consumer.handlers;

import edu.northeastern.hanafeng.chatsystem.common.interfaces.SqsMessageHandler;
import edu.northeastern.hanafeng.chatsystem.common.services.CloudWatchMetricsService;
import edu.northeastern.hanafeng.chatsystem.model.BroadcastMessage;
import edu.northeastern.hanafeng.chatsystem.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor
@Profile("consumer")
public class BroadcastSqsMessageHandler implements SqsMessageHandler {

    private final SimpMessagingTemplate messagingTemplate;
    private final CloudWatchMetricsService metricsService;

    @Value("${websocketchat.consumer.api.broadcast.topic-prefix}")
    private String topicPrefix;

    @Override
    public void handleMessage(String roomId, ChatMessage chatMessage) throws Exception {
        // Create broadcast message
        BroadcastMessage broadcastMessage = new BroadcastMessage();
        broadcastMessage.setChatMessage(chatMessage);
        broadcastMessage.setBroadcastTimestamp(Instant.now());
        broadcastMessage.setRoomId(roomId);

        // Publish to STOMP topic for the room
        String destination = topicPrefix + "/" + roomId;
        messagingTemplate.convertAndSend(destination, broadcastMessage);

        log.info("Published message to topic: {} with content: {}", destination, chatMessage);

        // Record metrics - don't let metrics failure break message delivery
        try {
            metricsService.recordMetric("BroadcastMessages", 1.0);
        } catch (Exception e) {
            log.error("Failed to record metrics for broadcast to room {}: {}", roomId, e.getMessage());
        }
    }
}
