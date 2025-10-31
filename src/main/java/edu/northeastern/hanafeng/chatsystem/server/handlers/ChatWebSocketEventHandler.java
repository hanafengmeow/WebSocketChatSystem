package edu.northeastern.hanafeng.chatsystem.server.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.hanafeng.chatsystem.common.services.CloudWatchMetricsService;
import edu.northeastern.hanafeng.chatsystem.common.services.SqsMessageService;
import edu.northeastern.hanafeng.chatsystem.model.ChatMessage;
import edu.northeastern.hanafeng.chatsystem.model.ResponseMessage;
import edu.northeastern.hanafeng.chatsystem.server.components.ClientSessionManager;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("server")
public class ChatWebSocketEventHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final CloudWatchMetricsService metricsService;
    private final SqsMessageService sqsMessageService;
    private final ClientSessionManager clientSessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            String roomId = (String) session.getAttributes().get("roomId");

            if (roomId == null) {
                log.error("Connection established without roomId, closing session: {}", session.getId());
                session.close(CloseStatus.SERVER_ERROR.withReason("Missing roomId"));
                return;
            }

            clientSessionManager.addSession(roomId, session);
        } catch (Exception e) {
            log.error("Error in afterConnectionEstablished for session {}: {}", session.getId(), e.getMessage(), e);
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (Exception closeEx) {
                log.error("Failed to close session {} after error: {}", session.getId(), closeEx.getMessage());
            }
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.info("Receive Message: {}", message.getPayload());

        try {
            ChatMessage chatMessage = objectMapper.readValue(message.getPayload(), ChatMessage.class);
            Set<ConstraintViolation<ChatMessage>> violations = validator.validate(chatMessage);
            if (!violations.isEmpty()) {
                sendErrorMessage(session, violations.iterator().next().getMessage());
            } else {
                sendSuccessMessage(session, chatMessage);
            }
        } catch (JsonProcessingException e) {
            log.error("JSON parsing error from session {}: {}", session.getId(), e.getMessage());
            sendErrorMessage(session, "Invalid JSON format: " + e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Session {} closed while processing message: {}", session.getId(), e.getMessage());
            // Don't try to send error - session is already closed
        } catch (Exception e) {
            log.error("Error handling message from session {}: {}", session.getId(), e.getMessage(), e);
            try {
                sendErrorMessage(session, "Server error: " + e.getMessage());
            } catch (Exception sendEx) {
                log.error("Failed to send error message to session {}: {}", session.getId(), sendEx.getMessage());
            }
        }
    }

    private void sendResponseMessage(
            WebSocketSession session, ChatMessage chatMessage, String status, String error) throws Exception {

        if (!session.isOpen()) {
            log.warn("Cannot send response - session {} is already closed", session.getId());
            return;
        }

        ResponseMessage responseMessage = new ResponseMessage();
        responseMessage.setEcho(chatMessage);
        responseMessage.setServerTimestamp(Instant.now());
        responseMessage.setStatus(status);
        responseMessage.setError(error);
        String responseJson = objectMapper.writeValueAsString(responseMessage);
        log.info("Response JSON: {}", responseJson);
        session.sendMessage(new TextMessage(responseJson));
    }

    private void sendSuccessMessage(WebSocketSession session, ChatMessage chatMessage) {
        try {
            sendResponseMessage(session, chatMessage, "success", null);
        } catch (Exception e) {
            log.error("Failed to send success response to session {}: {}", session.getId(), e.getMessage(), e);
            return; // Don't continue if we can't send response
        }

        try {
            metricsService.recordMetric("SuccessfulMessages", 1.0);
        } catch (Exception e) {
            log.error("Failed to record metrics for session {}: {}", session.getId(), e.getMessage());
            // Continue - metrics failure shouldn't block message delivery
        }

        String roomId = (String) session.getAttributes().get("roomId");
        if (roomId != null) {
            try {
                sqsMessageService.sendChatMessageToQueue(chatMessage, roomId);
            } catch (Exception e) {
                log.error("Failed to send message to SQS for room {}, session {}: {}",
                         roomId, session.getId(), e.getMessage(), e);
                // Message already acknowledged to client - log but don't fail
            }
        } else {
            log.error("Cannot send to SQS: roomId is null for session {}", session.getId());
        }
    }

    private void sendErrorMessage(WebSocketSession session, String error) {
        try {
            sendResponseMessage(session, null, "error", error);
        } catch (Exception e) {
            log.error("Failed to send error response to session {}: {}", session.getId(), e.getMessage(), e);
        }

        try {
            metricsService.recordMetric("FailedMessages", 1.0);
        } catch (Exception e) {
            log.error("Failed to record error metrics for session {}: {}", session.getId(), e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        try {
            String roomId = (String) session.getAttributes().get("roomId");

            if (roomId != null) {
                clientSessionManager.removeSession(roomId, session);
            }

            log.info("Client disconnected from room: {}, session: {}, status: {}", roomId, session.getId(), status);
        } catch (Exception e) {
            log.error("Error in afterConnectionClosed for session {}: {}", session.getId(), e.getMessage(), e);
        }
    }
}
