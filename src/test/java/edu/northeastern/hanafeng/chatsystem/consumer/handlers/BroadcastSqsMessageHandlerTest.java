package edu.northeastern.hanafeng.chatsystem.consumer.handlers;

import edu.northeastern.hanafeng.chatsystem.common.services.CloudWatchMetricsService;
import edu.northeastern.hanafeng.chatsystem.model.BroadcastMessage;
import edu.northeastern.hanafeng.chatsystem.model.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BroadcastSqsMessageHandlerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private CloudWatchMetricsService metricsService;

    private BroadcastSqsMessageHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BroadcastSqsMessageHandler(messagingTemplate, metricsService);
        ReflectionTestUtils.setField(handler, "topicPrefix", "/topic");
    }

    @Test
    void testHandleMessage_Success() throws Exception {
        // Given
        String roomId = "123";
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUserId("user1");
        chatMessage.setUsername("testuser");
        chatMessage.setMessage("Hello World");
        chatMessage.setTimestamp(Instant.parse("2025-01-25T10:00:00Z"));
        chatMessage.setMessageType("TEXT");

        // When
        handler.handleMessage(roomId, chatMessage);

        // Then
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BroadcastMessage> messageCaptor = ArgumentCaptor.forClass(BroadcastMessage.class);

        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), messageCaptor.capture());
        verify(metricsService).recordMetric("BroadcastMessages", 1.0);

        assertEquals("/topic/123", destinationCaptor.getValue());

        BroadcastMessage broadcastMessage = messageCaptor.getValue();
        assertNotNull(broadcastMessage);
        assertEquals(roomId, broadcastMessage.getRoomId());
        assertEquals(chatMessage, broadcastMessage.getChatMessage());
        assertNotNull(broadcastMessage.getBroadcastTimestamp());
    }

    @Test
    void testHandleMessage_DifferentRoomIds() throws Exception {
        // Given
        ChatMessage message1 = new ChatMessage();
        message1.setUserId("user1");
        message1.setMessage("Message 1");

        ChatMessage message2 = new ChatMessage();
        message2.setUserId("user2");
        message2.setMessage("Message 2");

        // When
        handler.handleMessage("100", message1);
        handler.handleMessage("200", message2);

        // Then
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate, times(2)).convertAndSend(destinationCaptor.capture(), any(BroadcastMessage.class));

        assertEquals("/topic/100", destinationCaptor.getAllValues().get(0));
        assertEquals("/topic/200", destinationCaptor.getAllValues().get(1));
        verify(metricsService, times(2)).recordMetric("BroadcastMessages", 1.0);
    }

    @Test
    void testHandleMessage_BroadcastMessageContainsCorrectData() throws Exception {
        // Given
        String roomId = "456";
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUserId("user123");
        chatMessage.setUsername("john");
        chatMessage.setMessage("Test message");
        chatMessage.setTimestamp(Instant.parse("2025-01-26T15:30:00Z"));
        chatMessage.setMessageType("TEXT");

        Instant beforeCall = Instant.now();

        // When
        handler.handleMessage(roomId, chatMessage);

        Instant afterCall = Instant.now();

        // Then
        ArgumentCaptor<BroadcastMessage> captor = ArgumentCaptor.forClass(BroadcastMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/456"), captor.capture());

        BroadcastMessage result = captor.getValue();
        assertEquals("456", result.getRoomId());
        assertEquals("user123", result.getChatMessage().getUserId());
        assertEquals("john", result.getChatMessage().getUsername());
        assertEquals("Test message", result.getChatMessage().getMessage());
        assertTrue(result.getBroadcastTimestamp().isAfter(beforeCall.minusSeconds(1)));
        assertTrue(result.getBroadcastTimestamp().isBefore(afterCall.plusSeconds(1)));
    }

    @Test
    void testHandleMessage_MessagingTemplateThrowsException() {
        // Given
        String roomId = "789";
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUserId("user1");
        chatMessage.setMessage("Test");

        doThrow(new RuntimeException("Messaging error"))
                .when(messagingTemplate).convertAndSend(anyString(), any(BroadcastMessage.class));

        // When/Then
        assertThrows(RuntimeException.class, () -> handler.handleMessage(roomId, chatMessage));

        // Metrics should not be recorded if messaging fails
        verify(metricsService, never()).recordMetric(anyString(), anyDouble());
    }

    @Test
    void testHandleMessage_MetricsServiceThrowsException() throws Exception {
        // Given
        String roomId = "999";
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUserId("user1");
        chatMessage.setMessage("Test");

        doThrow(new RuntimeException("Metrics error"))
                .when(metricsService).recordMetric(anyString(), anyDouble());

        // When - should succeed even if metrics fail (metrics failure is caught)
        handler.handleMessage(roomId, chatMessage);

        // Then - verify message was still broadcast despite metrics failure
        verify(messagingTemplate).convertAndSend(eq("/topic/999"), any(BroadcastMessage.class));
        verify(metricsService).recordMetric("BroadcastMessages", 1.0);
    }

    @Test
    void testHandleMessage_NullChatMessageFields() throws Exception {
        // Given
        String roomId = "111";
        ChatMessage chatMessage = new ChatMessage();
        // Only set required fields, leave others null
        chatMessage.setUserId("user1");

        // When
        handler.handleMessage(roomId, chatMessage);

        // Then - should still process successfully
        ArgumentCaptor<BroadcastMessage> captor = ArgumentCaptor.forClass(BroadcastMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/111"), captor.capture());

        BroadcastMessage result = captor.getValue();
        assertEquals("111", result.getRoomId());
        assertNotNull(result.getChatMessage());
        assertEquals("user1", result.getChatMessage().getUserId());
        assertNotNull(result.getBroadcastTimestamp());
    }
}
