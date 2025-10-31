package edu.northeastern.hanafeng.chatsystem.server.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsumerDiscoveryServiceTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    private ConsumerDiscoveryService consumerDiscoveryService;

    @BeforeEach
    void setUp() {
        consumerDiscoveryService = new ConsumerDiscoveryService(dynamoDbClient);
        ReflectionTestUtils.setField(consumerDiscoveryService, "tableName", "ConsumerRegistry");
    }

    @Test
    void testFindConsumerEndpoint_Found() {
        // Given
        String roomId = "123";
        String expectedEndpoint = "ws://consumer1:8080/broadcast";

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("roomId", AttributeValue.builder().s(roomId).build());
        item.put("endpoint", AttributeValue.builder().s(expectedEndpoint).build());

        QueryResponse response = QueryResponse.builder()
                .items(item)
                .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        String result = consumerDiscoveryService.findConsumerEndpoint(roomId);

        // Then
        assertEquals(expectedEndpoint, result);
        verify(dynamoDbClient).query(any(QueryRequest.class));
    }

    @Test
    void testFindConsumerEndpoint_NotFound() {
        // Given
        String roomId = "123";

        QueryResponse response = QueryResponse.builder()
                .items(Collections.emptyList())
                .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        String result = consumerDiscoveryService.findConsumerEndpoint(roomId);

        // Then
        assertNull(result);
        verify(dynamoDbClient).query(any(QueryRequest.class));
    }

    @Test
    void testFindConsumerEndpoint_NullEndpoint() {
        // Given
        String roomId = "123";

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("roomId", AttributeValue.builder().s(roomId).build());
        // endpoint field is missing

        QueryResponse response = QueryResponse.builder()
                .items(item)
                .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        String result = consumerDiscoveryService.findConsumerEndpoint(roomId);

        // Then
        assertNull(result);
        verify(dynamoDbClient).query(any(QueryRequest.class));
    }

    @Test
    void testFindConsumerEndpoint_DynamoDbException() {
        // Given
        String roomId = "123";

        when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenThrow(DynamoDbException.builder()
                        .message("DynamoDB error")
                        .build());

        // When
        String result = consumerDiscoveryService.findConsumerEndpoint(roomId);

        // Then
        assertNull(result);
        verify(dynamoDbClient).query(any(QueryRequest.class));
    }

    @Test
    void testFindConsumerEndpoint_GenericException() {
        // Given
        String roomId = "123";

        when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        // When
        String result = consumerDiscoveryService.findConsumerEndpoint(roomId);

        // Then
        assertNull(result);
        verify(dynamoDbClient).query(any(QueryRequest.class));
    }

    @Test
    void testFindConsumerEndpoint_QueryParameters() {
        // Given
        String roomId = "123";

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("roomId", AttributeValue.builder().s(roomId).build());
        item.put("endpoint", AttributeValue.builder().s("ws://test").build());

        QueryResponse response = QueryResponse.builder()
                .items(item)
                .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        consumerDiscoveryService.findConsumerEndpoint(roomId);

        // Then
        verify(dynamoDbClient).query(argThat((QueryRequest request) ->
            request.tableName().equals("ConsumerRegistry") &&
            request.keyConditionExpression().equals("roomId = :roomId") &&
            request.limit() == 1
        ));
    }
}
