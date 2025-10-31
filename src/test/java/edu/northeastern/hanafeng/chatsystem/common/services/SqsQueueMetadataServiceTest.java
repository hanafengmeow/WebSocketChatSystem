package edu.northeastern.hanafeng.chatsystem.common.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SqsQueueMetadataServiceTest {

    @Mock
    private SqsClient sqsClient;

    private SqsQueueMetadataService service;

    @BeforeEach
    void setUp() {
        service = new SqsQueueMetadataService(sqsClient);
        ReflectionTestUtils.setField(service, "queueNamePattern", "chat-room-{roomId}");
        ReflectionTestUtils.setField(service, "dlqName", "chat-room-dlq");
    }

    @Test
    void testGetChatRoomQueueName() {
        // When
        String queueName = service.getChatRoomQueueName("123");

        // Then
        assertEquals("chat-room-123", queueName);
    }

    @Test
    void testGetChatRoomQueueName_DifferentRoomIds() {
        // Then
        assertEquals("chat-room-1", service.getChatRoomQueueName("1"));
        assertEquals("chat-room-999", service.getChatRoomQueueName("999"));
        assertEquals("chat-room-abc", service.getChatRoomQueueName("abc"));
    }

    @Test
    void testGetChatRoomQueueUrl_Success() {
        // Given
        GetQueueUrlResponse response = GetQueueUrlResponse.builder()
                .queueUrl("https://sqs.us-east-1.amazonaws.com/123456789/chat-room-123")
                .build();

        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class))).thenReturn(response);

        // When
        String queueUrl = service.getChatRoomQueueUrl("123");

        // Then
        assertEquals("https://sqs.us-east-1.amazonaws.com/123456789/chat-room-123", queueUrl);
        verify(sqsClient).getQueueUrl(argThat((GetQueueUrlRequest req) ->
                req.queueName().equals("chat-room-123")
        ));
    }

    @Test
    void testGetChatRoomQueueUrl_QueueDoesNotExist() {
        // Given
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenThrow(QueueDoesNotExistException.builder().message("Queue does not exist").build());

        // When
        String queueUrl = service.getChatRoomQueueUrl("123");

        // Then
        assertNull(queueUrl);
    }

    @Test
    void testGetChatRoomDlqUrl_Success() {
        // Given
        GetQueueUrlResponse response = GetQueueUrlResponse.builder()
                .queueUrl("https://sqs.us-east-1.amazonaws.com/123456789/chat-room-dlq")
                .build();

        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class))).thenReturn(response);

        // When
        String dlqUrl = service.getChatRoomDlqUrl();

        // Then
        assertEquals("https://sqs.us-east-1.amazonaws.com/123456789/chat-room-dlq", dlqUrl);
        verify(sqsClient).getQueueUrl(argThat((GetQueueUrlRequest req) ->
                req.queueName().equals("chat-room-dlq")
        ));
    }

    @Test
    void testGetChatRoomDlqUrl_QueueDoesNotExist() {
        // Given
        when(sqsClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenThrow(QueueDoesNotExistException.builder().message("DLQ does not exist").build());

        // When
        String dlqUrl = service.getChatRoomDlqUrl();

        // Then
        assertNull(dlqUrl);
    }

    @Test
    void testEvictChatRoomQueueUrlFromCache() {
        // When/Then - should not throw exception
        assertDoesNotThrow(() -> service.evictChatRoomQueueUrlFromCache("123"));
    }

    @Test
    void testEvictChatRoomDlqUrlFromCache() {
        // When/Then - should not throw exception
        assertDoesNotThrow(() -> service.evictChatRoomDlqUrlFromCache());
    }

    @Test
    void testGetChatRoomQueueName_WithDifferentPattern() {
        // Given
        ReflectionTestUtils.setField(service, "queueNamePattern", "room-{roomId}-queue");

        // When
        String queueName = service.getChatRoomQueueName("456");

        // Then
        assertEquals("room-456-queue", queueName);
    }
}
