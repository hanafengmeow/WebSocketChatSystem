package edu.northeastern.hanafeng.chatsystem.server.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Type;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration test to reproduce and validate the STOMP message conversion issue.
 *
 * This test simulates the exact scenario where:
 * 1. Consumer sends BroadcastMessage via STOMP
 * 2. Server's BroadcastStompEventHandler receives it
 * 3. Message is deserialized and broadcast to WebSocket clients
 */
@ExtendWith(MockitoExtension.class)
class BroadcastStompEventHandlerIntegrationTest {

    private ObjectMapper objectMapper;

    @Mock
    private ClientSessionManager clientSessionManager;

    @Mock
    private StompHeaders stompHeaders;

    private BroadcastStompEventHandler handler;

    @BeforeEach
    void setUp() {
        // Use real ObjectMapper to test actual serialization/deserialization
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Register JavaTimeModule for Instant support

        handler = new BroadcastStompEventHandler(clientSessionManager);
        ReflectionTestUtils.setField(handler, "topicPrefix", "/topic/room");
    }

    @Test
    void testGetPayloadType_ReturnsBroadcastMessage() {
        // Given - headers (can be null, not used in getPayloadType)

        // When
        Type payloadType = handler.getPayloadType(stompHeaders);

        // Then
        assertEquals(BroadcastMessage.class, payloadType);
    }

    @Test
    void testHandleFrame_WithRealBroadcastMessage() throws Exception {
        // Given - Create a real BroadcastMessage (Jackson will deserialize JSON to this)
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUserId("user123");
        chatMessage.setUsername("testuser");
        chatMessage.setMessage("Hello World");
        chatMessage.setTimestamp(Instant.parse("2025-01-31T10:00:00Z"));
        chatMessage.setMessageType("TEXT");
        chatMessage.setRoomId("room1");
        chatMessage.setMessageId("msg-123");

        BroadcastMessage broadcastMessage = new BroadcastMessage();
        broadcastMessage.setChatMessage(chatMessage);
        broadcastMessage.setBroadcastTimestamp(Instant.parse("2025-01-31T10:00:01Z"));
        broadcastMessage.setRoomId("room1");

        // Mock STOMP headers
        when(stompHeaders.getDestination()).thenReturn("/topic/room/room1");

        // When - Jackson already deserialized JSON to BroadcastMessage
        handler.handleFrame(stompHeaders, broadcastMessage);

        // Then
        ArgumentCaptor<String> roomIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);

        verify(clientSessionManager).broadcastToRoom(roomIdCaptor.capture(), messageCaptor.capture());

        assertEquals("room1", roomIdCaptor.getValue());

        ChatMessage capturedMessage = messageCaptor.getValue();
        assertEquals("user123", capturedMessage.getUserId());
        assertEquals("testuser", capturedMessage.getUsername());
        assertEquals("Hello World", capturedMessage.getMessage());
        assertEquals("TEXT", capturedMessage.getMessageType());
        assertEquals("room1", capturedMessage.getRoomId());
        assertEquals("msg-123", capturedMessage.getMessageId());
    }

    @Test
    void testHandleFrame_WithNonBroadcastMessage() {
        // Given - wrong payload type (Jackson converter wouldn't produce this in reality)
        when(stompHeaders.getDestination()).thenReturn("/topic/room/room1");
        String wrongType = "not a BroadcastMessage";

        // When
        handler.handleFrame(stompHeaders, wrongType);

        // Then - should log error and not call broadcastToRoom
        verify(clientSessionManager, never()).broadcastToRoom(any(), any());
    }

    @Test
    void testHandleFrame_WithMissingChatMessage() throws Exception {
        // Given - BroadcastMessage with null chatMessage
        BroadcastMessage broadcastMessage = new BroadcastMessage();
        broadcastMessage.setBroadcastTimestamp(Instant.now());
        broadcastMessage.setRoomId("room1");
        broadcastMessage.setChatMessage(null); // Missing

        when(stompHeaders.getDestination()).thenReturn("/topic/room/room1");

        // When
        handler.handleFrame(stompHeaders, broadcastMessage);

        // Then - should handle gracefully (may throw NPE or log error)
        // This test documents the current behavior
    }

    @Test
    void testHandleFrame_WithDifferentTimestampFormats() throws Exception {
        // Given - Test various timestamp formats
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUserId("user1");
        chatMessage.setMessage("test");
        chatMessage.setTimestamp(Instant.parse("2025-01-31T10:30:45.123Z")); // With milliseconds

        BroadcastMessage broadcastMessage = new BroadcastMessage();
        broadcastMessage.setChatMessage(chatMessage);
        broadcastMessage.setBroadcastTimestamp(Instant.parse("2025-01-31T10:30:45.456789Z")); // With microseconds
        broadcastMessage.setRoomId("room1");

        when(stompHeaders.getDestination()).thenReturn("/topic/room/room1");

        // When
        handler.handleFrame(stompHeaders, broadcastMessage);

        // Then
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(clientSessionManager).broadcastToRoom(eq("room1"), messageCaptor.capture());

        assertNotNull(messageCaptor.getValue().getTimestamp());
    }

    @Test
    void testHandleFrame_SimulatesActualConsumerPayload() throws Exception {
        // Given - Simulate the EXACT payload the consumer sends (after Jackson deserialization)
        String actualConsumerPayload = """
                {
                  "chatMessage": {
                    "userId": "user-456",
                    "roomId": "room2",
                    "messageId": "msg-456",
                    "username": "alice",
                    "message": "Test from consumer",
                    "timestamp": "2025-01-31T14:22:00Z",
                    "messageType": "TEXT"
                  },
                  "broadcastTimestamp": "2025-01-31T14:22:00.123Z",
                  "roomId": "room2"
                }
                """;

        // Deserialize to BroadcastMessage (this is what Jackson does)
        BroadcastMessage broadcastMessage = objectMapper.readValue(actualConsumerPayload, BroadcastMessage.class);

        when(stompHeaders.getDestination()).thenReturn("/topic/room/room2");

        // When
        handler.handleFrame(stompHeaders, broadcastMessage);

        // Then
        ArgumentCaptor<String> roomIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);

        verify(clientSessionManager).broadcastToRoom(roomIdCaptor.capture(), messageCaptor.capture());

        assertEquals("room2", roomIdCaptor.getValue());

        ChatMessage capturedMessage = messageCaptor.getValue();
        assertEquals("user-456", capturedMessage.getUserId());
        assertEquals("alice", capturedMessage.getUsername());
        assertEquals("Test from consumer", capturedMessage.getMessage());
        assertEquals("room2", capturedMessage.getRoomId());
    }
}
