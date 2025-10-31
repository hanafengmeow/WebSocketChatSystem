package edu.northeastern.hanafeng.chatsystem.server.handlers;

import edu.northeastern.hanafeng.chatsystem.model.BroadcastMessage;
import edu.northeastern.hanafeng.chatsystem.model.ChatMessage;
import edu.northeastern.hanafeng.chatsystem.server.components.ClientSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Type;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BroadcastStompEventHandlerTest {

    @Mock
    private ClientSessionManager clientSessionManager;

    @Mock
    private StompSession stompSession;

    @Mock
    private StompHeaders stompHeaders;

    private BroadcastStompEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BroadcastStompEventHandler(clientSessionManager);
        ReflectionTestUtils.setField(handler, "topicPrefix", "/topic/room");
    }

    @Test
    void testAfterConnected() {
        // Given
        when(stompSession.getSessionId()).thenReturn("session-123");

        // When
        handler.afterConnected(stompSession, stompHeaders);

        // Then - should log without error
        verify(stompSession).getSessionId();
    }

    @Test
    void testHandleException() {
        // Given
        when(stompSession.getSessionId()).thenReturn("session-123");
        Exception exception = new RuntimeException("Test exception");

        // When
        handler.handleException(stompSession, null, stompHeaders, new byte[0], exception);

        // Then - should log without error
        verify(stompSession).getSessionId();
    }

    @Test
    void testHandleTransportError() {
        // Given
        when(stompSession.getSessionId()).thenReturn("session-123");
        Exception exception = new RuntimeException("Transport error");

        // When
        handler.handleTransportError(stompSession, exception);

        // Then - should log without error
        verify(stompSession).getSessionId();
    }

    @Test
    void testGetPayloadType() {
        // When
        Type result = handler.getPayloadType(stompHeaders);

        // Then
        assertEquals(BroadcastMessage.class, result);
    }

    @Test
    void testHandleFrame_ValidMessage() throws Exception {
        // Given
        String destination = "/topic/room/123";

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUserId("user1");
        chatMessage.setUsername("test");
        chatMessage.setMessage("Hello");
        chatMessage.setTimestamp(Instant.now());
        chatMessage.setMessageType("TEXT");

        BroadcastMessage broadcastMessage = new BroadcastMessage();
        broadcastMessage.setChatMessage(chatMessage);
        broadcastMessage.setBroadcastTimestamp(Instant.parse("2025-01-25T10:00:00Z"));
        broadcastMessage.setRoomId("123");

        when(stompHeaders.getDestination()).thenReturn(destination);

        // When - Jackson already deserialized to BroadcastMessage
        handler.handleFrame(stompHeaders, broadcastMessage);

        // Then
        ArgumentCaptor<String> roomIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);

        verify(clientSessionManager).broadcastToRoom(roomIdCaptor.capture(), messageCaptor.capture());

        assertEquals("123", roomIdCaptor.getValue());
        assertEquals("user1", messageCaptor.getValue().getUserId());
    }

    @Test
    void testHandleFrame_InvalidDestination_Null() {
        // Given
        BroadcastMessage broadcastMessage = new BroadcastMessage();
        when(stompHeaders.getDestination()).thenReturn(null);

        // When
        handler.handleFrame(stompHeaders, broadcastMessage);

        // Then - should log warning and not broadcast
        verify(clientSessionManager, never()).broadcastToRoom(any(), any());
    }

    @Test
    void testHandleFrame_InvalidDestination_WrongPrefix() {
        // Given
        String destination = "/invalid/prefix/123";
        BroadcastMessage broadcastMessage = new BroadcastMessage();
        when(stompHeaders.getDestination()).thenReturn(destination);

        // When
        handler.handleFrame(stompHeaders, broadcastMessage);

        // Then - should log warning and not broadcast
        verify(clientSessionManager, never()).broadcastToRoom(any(), any());
    }

    @Test
    void testHandleFrame_WrongPayloadType() throws Exception {
        // Given
        String destination = "/topic/room/123";
        String wrongType = "not a BroadcastMessage";

        when(stompHeaders.getDestination()).thenReturn(destination);

        // When
        handler.handleFrame(stompHeaders, wrongType);

        // Then - should log error and not broadcast
        verify(clientSessionManager, never()).broadcastToRoom(any(), any());
    }

    @Test
    void testHandleFrame_BroadcastException() throws Exception {
        // Given
        String destination = "/topic/room/123";

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUserId("user1");

        BroadcastMessage broadcastMessage = new BroadcastMessage();
        broadcastMessage.setChatMessage(chatMessage);
        broadcastMessage.setRoomId("123");

        when(stompHeaders.getDestination()).thenReturn(destination);
        doThrow(new RuntimeException("Broadcast error"))
                .when(clientSessionManager).broadcastToRoom(any(), any());

        // When
        handler.handleFrame(stompHeaders, broadcastMessage);

        // Then - should log error but not throw exception
        verify(clientSessionManager).broadcastToRoom(eq("123"), eq(chatMessage));
    }
}
