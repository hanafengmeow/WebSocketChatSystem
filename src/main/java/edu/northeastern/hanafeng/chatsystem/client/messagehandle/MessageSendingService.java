package edu.northeastern.hanafeng.chatsystem.client.messagehandle;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.hanafeng.chatsystem.client.metrics.ClientMetricsAggregator;
import edu.northeastern.hanafeng.chatsystem.client.transport.WebSocketClientManager;
import edu.northeastern.hanafeng.chatsystem.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageSendingService {

    private final WebSocketClientManager wsClientManager;
    private final ObjectMapper objectMapper;
    private final ClientMetricsAggregator metricsAggregator;

    @Async("messageSenderExecutor")
    public CompletableFuture<Boolean> sendMessageAsync(String jsonMessage) {
        try {
            ChatMessage chatMessage = objectMapper.readValue(jsonMessage, ChatMessage.class);
            int roomId = Integer.parseInt(chatMessage.getRoomId());
            String messageId = chatMessage.getMessageId();

            wsClientManager.recordSent(roomId);

            var connection = wsClientManager.getConnection(roomId);
            int maxRetries = 3;
            boolean sent = false;

            for (int retry = 0; retry < maxRetries && !sent; retry++) {
                if (connection != null && connection.isOpen()) {
                    try {
                        connection.send(jsonMessage);
                        sent = true;
                        wsClientManager.recordSuccess(roomId);
                        metricsAggregator.incrementMessagesSent();
                        log.debug("[SUCCESS] Room={} | MessageId={}", roomId, messageId);
                    } catch (Exception e) {
                        if (retry < maxRetries - 1) {
                            Thread.sleep(100);
                        } else {
                            log.warn("[FAILED] Room={} | MessageId={} | Error={}", roomId, messageId, e.getMessage());
                        }
                    }
                } else if (retry < maxRetries - 1) {
                    Thread.sleep(100);
                    connection = wsClientManager.getConnection(roomId);
                }
            }

            if (!sent) {
                wsClientManager.recordFailure(roomId);
                metricsAggregator.incrementMessagesFailed();
            }

            return CompletableFuture.completedFuture(sent);

        } catch (Exception e) {
            log.error("[ERROR] Failed to send message", e);
            return CompletableFuture.completedFuture(false);
        }
    }
}