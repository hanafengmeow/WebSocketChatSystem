package edu.northeastern.hanafeng.chatsystem.server.components;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.hanafeng.chatsystem.common.services.CloudWatchMetricsService;
import edu.northeastern.hanafeng.chatsystem.model.ChatMessage;
import edu.northeastern.hanafeng.chatsystem.server.services.StompSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientSessionManagerTest {

    @Mock
    private StompSubscriptionService stompSubscriptionService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CloudWatchMetricsService metricsService;

    @Mock
    private WebSocketSession session1;

    @Mock
    private WebSocketSession session2;

    private ClientSessionManager clientSessionManager;

    @BeforeEach
    void setUp() {
        clientSessionManager = new ClientSessionManager(stompSubscriptionService, objectMapper, metricsService);
        lenient().when(session1.getId()).thenReturn("session-1");
        lenient().when(session1.isOpen()).thenReturn(true);
        lenient().when(session2.getId()).thenReturn("session-2");
        lenient().when(session2.isOpen()).thenReturn(true);
    }

    @Test
    void testAddSession_FirstClient_ShouldSubscribe() {
        // When
        clientSessionManager.addSession("room1", session1);

        // Then
        verify(stompSubscriptionService).subscribe("room1");
    }

    @Test
    void testAddSession_SecondClient_ShouldNotSubscribe() {
        // Given
        clientSessionManager.addSession("room1", session1);
        reset(stompSubscriptionService);

        // When
        clientSessionManager.addSession("room1", session2);

        // Then
        verify(stompSubscriptionService, never()).subscribe(any());
    }

    @Test
    void testRemoveSession_LastClient_ShouldUnsubscribe() {
        // Given
        clientSessionManager.addSession("room1", session1);

        // When
        clientSessionManager.removeSession("room1", session1);

        // Then
        verify(stompSubscriptionService).unsubscribe("room1");
    }

    @Test
    void testRemoveSession_OtherClientsRemain_ShouldNotUnsubscribe() {
        // Given
        clientSessionManager.addSession("room1", session1);
        clientSessionManager.addSession("room1", session2);
        reset(stompSubscriptionService);

        // When
        clientSessionManager.removeSession("room1", session1);

        // Then
        verify(stompSubscriptionService, never()).unsubscribe(any());
    }

    @Test
    void testRemoveSession_RoomDoesNotExist() {
        // When
        clientSessionManager.removeSession("nonexistent", session1);

        // Then
        verify(stompSubscriptionService, never()).unsubscribe(any());
    }

    @Test
    void testRemoveSession_SessionNotInRoom() {
        // Given
        clientSessionManager.addSession("room1", session1);

        // When
        clientSessionManager.removeSession("room1", session2);

        // Then
        verify(stompSubscriptionService, never()).unsubscribe(any());
    }

    @Test
    void testBroadcastToRoom_Success() throws Exception {
        // Given
        clientSessionManager.addSession("room1", session1);
        clientSessionManager.addSession("room1", session2);

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUserId("user1");
        chatMessage.setUsername("testuser");
        chatMessage.setMessage("Hello");
        chatMessage.setTimestamp(Instant.now());
        chatMessage.setMessageType("TEXT");

        String messageJson = "{\"userId\":\"user1\"}";
        when(objectMapper.writeValueAsString(chatMessage)).thenReturn(messageJson);

        // When
        clientSessionManager.broadcastToRoom("room1", chatMessage);

        // Then
        verify(session1).sendMessage(any(TextMessage.class));
        verify(session2).sendMessage(any(TextMessage.class));
        verify(metricsService).recordMetric("BroadcastSuccess", 2.0);
    }

    @Test
    void testBroadcastToRoom_NoClients() {
        // Given
        ChatMessage chatMessage = new ChatMessage();

        // When
        clientSessionManager.broadcastToRoom("room1", chatMessage);

        // Then
        verify(metricsService, never()).recordMetric(any(), anyDouble());
    }

    @Test
    void testBroadcastToRoom_ClosedSession() throws Exception {
        // Given
        clientSessionManager.addSession("room1", session1);
        when(session1.isOpen()).thenReturn(false);

        ChatMessage chatMessage = new ChatMessage();
        String messageJson = "{\"userId\":\"user1\"}";
        when(objectMapper.writeValueAsString(chatMessage)).thenReturn(messageJson);

        // When
        clientSessionManager.broadcastToRoom("room1", chatMessage);

        // Then
        verify(session1, never()).sendMessage(any());
        verify(metricsService).recordMetric("BroadcastFailure", 1.0);
    }

    @Test
    void testBroadcastToRoom_SendException() throws Exception {
        // Given
        clientSessionManager.addSession("room1", session1);

        ChatMessage chatMessage = new ChatMessage();
        String messageJson = "{\"userId\":\"user1\"}";
        when(objectMapper.writeValueAsString(chatMessage)).thenReturn(messageJson);
        doThrow(new RuntimeException("Send failed")).when(session1).sendMessage(any());

        // When
        clientSessionManager.broadcastToRoom("room1", chatMessage);

        // Then
        verify(metricsService).recordMetric("BroadcastFailure", 1.0);
    }
}
