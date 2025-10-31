package edu.northeastern.hanafeng.chatsystem.server.components;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.hanafeng.chatsystem.common.services.CloudWatchMetricsService;
import edu.northeastern.hanafeng.chatsystem.common.services.SqsMessageService;
import edu.northeastern.hanafeng.chatsystem.model.ChatMessage;
import edu.northeastern.hanafeng.chatsystem.model.ResponseMessage;
import edu.northeastern.hanafeng.chatsystem.server.handlers.ChatWebSocketEventHandler;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketEventHandlerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Validator validator;

    @Mock
    private CloudWatchMetricsService metricsService;

    @Mock
    private SqsMessageService sqsMessageService;

    @Mock
    private ClientSessionManager clientSessionManager;

    @Mock
    private WebSocketSession session;

    private ChatWebSocketEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ChatWebSocketEventHandler(objectMapper, validator, metricsService, sqsMessageService, clientSessionManager);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("roomId", "1");
        lenient().when(session.getAttributes()).thenReturn(attributes);
        lenient().when(session.getId()).thenReturn("test-session-id");
        lenient().when(session.isOpen()).thenReturn(true);
    }

    @Test
    void testHandleTextMessage_Success() throws Exception {
        // Given
        String payload = "{\"userId\":\"123\",\"username\":\"user123\",\"message\":\"test\",\"timestamp\":\"2025-01-25T10:00:00Z\",\"messageType\":\"TEXT\"}";
        TextMessage message = new TextMessage(payload);

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUserId("123");
        chatMessage.setUsername("user123");
        chatMessage.setMessage("test");
        chatMessage.setTimestamp(Instant.parse("2025-01-25T10:00:00Z"));
        chatMessage.setMessageType("TEXT");

        when(objectMapper.readValue(payload, ChatMessage.class)).thenReturn(chatMessage);
        when(validator.validate(chatMessage)).thenReturn(Collections.emptySet());
        when(objectMapper.writeValueAsString(any(ResponseMessage.class))).thenReturn("{\"status\":\"success\"}");

        // When
        handler.handleTextMessage(session, message);

        // Then
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        verify(objectMapper).writeValueAsString(any(ResponseMessage.class));
        verify(metricsService).recordMetric("SuccessfulMessages", 1.0);

        TextMessage sentMessage = captor.getValue();
        assertEquals("{\"status\":\"success\"}", sentMessage.getPayload());
    }

    @Test
    void testHandleTextMessage_ValidationError() throws Exception {
        // Given
        String payload = "{\"userId\":\"\",\"username\":\"user123\",\"message\":\"test\",\"timestamp\":\"2025-01-25T10:00:00Z\",\"messageType\":\"TEXT\"}";
        TextMessage message = new TextMessage(payload);

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUserId("");
        chatMessage.setUsername("user123");
        chatMessage.setMessage("test");
        chatMessage.setTimestamp(Instant.parse("2025-01-25T10:00:00Z"));
        chatMessage.setMessageType("TEXT");

        @SuppressWarnings("unchecked")
        ConstraintViolation<ChatMessage> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("userId missing");

        when(objectMapper.readValue(payload, ChatMessage.class)).thenReturn(chatMessage);
        when(validator.validate(chatMessage)).thenReturn(Set.of(violation));
        when(objectMapper.writeValueAsString(any(ResponseMessage.class))).thenReturn("{\"status\":\"error\",\"error\":\"userId missing\"}");

        // When
        handler.handleTextMessage(session, message);

        // Then
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        verify(metricsService).recordMetric("FailedMessages", 1.0);

        TextMessage sentMessage = captor.getValue();
        assertEquals("{\"status\":\"error\",\"error\":\"userId missing\"}", sentMessage.getPayload());
    }

    @Test
    void testHandleTextMessage_InvalidFormat() throws Exception {
        // Given
        String payload = "invalid json";
        TextMessage message = new TextMessage(payload);

        when(objectMapper.readValue(payload, ChatMessage.class)).thenThrow(new RuntimeException("Invalid JSON"));
        when(objectMapper.writeValueAsString(any(ResponseMessage.class))).thenReturn("{\"status\":\"error\"}");

        // When
        handler.handleTextMessage(session, message);

        // Then
        verify(session).sendMessage(any(TextMessage.class));
        verify(objectMapper).writeValueAsString(any(ResponseMessage.class));
        verify(metricsService).recordMetric("FailedMessages", 1.0);
    }

    @Test
    void testAfterConnectionEstablished() throws Exception {
        // When
        handler.afterConnectionEstablished(session);

        // Then
        verify(session).getAttributes();
        verify(clientSessionManager).addSession("1", session);
    }

    @Test
    void testAfterConnectionClosed() throws Exception {
        // Given
        handler.afterConnectionEstablished(session);

        // When
        handler.afterConnectionClosed(session, null);

        // Then
        verify(clientSessionManager).removeSession("1", session);
    }
}
