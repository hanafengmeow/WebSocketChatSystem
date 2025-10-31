package edu.northeastern.hanafeng.chatsystem.consumer.services;

import edu.northeastern.hanafeng.chatsystem.common.utils.EnvironmentUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsumerRegistryServiceTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private EnvironmentUtils environmentUtils;

    private ConsumerRegistryService service;

    @BeforeEach
    void setUp() {
        service = new ConsumerRegistryService(dynamoDbClient, environmentUtils);
        ReflectionTestUtils.setField(service, "tableName", "ConsumerRegistry");
        ReflectionTestUtils.setField(service, "port", 8080);
        ReflectionTestUtils.setField(service, "maxRooms", 3);
        ReflectionTestUtils.setField(service, "ttlSeconds", 60);
        ReflectionTestUtils.setField(service, "broadcastEndpoint", "/broadcast");

        lenient().when(environmentUtils.getHostname()).thenReturn("consumer-instance-1");
        lenient().when(environmentUtils.getInternalIpAddress()).thenReturn("192.168.1.100");
    }

    @Test
    void testInitialize_Success() {
        // When
        service.initialize();

        // Then
        verify(environmentUtils).getHostname();
        verify(environmentUtils).getInternalIpAddress();

        // Should register all rooms (1 to maxRooms = 3)
        verify(dynamoDbClient, times(3)).putItem(any(PutItemRequest.class));
    }

    @Test
    void testInitialize_BuildsCorrectEndpoint() {
        // When
        service.initialize();

        // Then
        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient, atLeastOnce()).putItem(captor.capture());

        PutItemRequest request = captor.getValue();
        Map<String, AttributeValue> item = request.item();

        assertEquals("ws://192.168.1.100:8080/broadcast", item.get("endpoint").s());
        assertEquals("consumer-instance-1", item.get("consumerId").s());
    }

    @Test
    void testInitialize_RegistersAllRooms() {
        // When
        service.initialize();

        // Then
        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient, times(3)).putItem(captor.capture());

        List<PutItemRequest> requests = captor.getAllValues();

        // Verify all room IDs are registered (order may vary due to parallel execution)
        Set<String> roomIds = requests.stream()
                .map(req -> req.item().get("roomId").s())
                .collect(Collectors.toSet());
        assertEquals(Set.of("1", "2", "3"), roomIds);

        // Verify all have same endpoint and consumerId
        for (PutItemRequest request : requests) {
            Map<String, AttributeValue> item = request.item();
            assertEquals("ConsumerRegistry", request.tableName());
            assertEquals("ws://192.168.1.100:8080/broadcast", item.get("endpoint").s());
            assertEquals("consumer-instance-1", item.get("consumerId").s());
            assertNotNull(item.get("lastHeartbeat"));
            assertNotNull(item.get("expiresAt"));
        }
    }

    @Test
    void testInitialize_SetsTTLCorrectly() {
        // Given
        long beforeInitialize = Instant.now().getEpochSecond();

        // When
        service.initialize();

        long afterInitialize = Instant.now().getEpochSecond();

        // Then
        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient, atLeastOnce()).putItem(captor.capture());

        PutItemRequest request = captor.getValue();
        Map<String, AttributeValue> item = request.item();

        long lastHeartbeat = Long.parseLong(item.get("lastHeartbeat").n());
        long expiresAt = Long.parseLong(item.get("expiresAt").n());

        // Verify lastHeartbeat is around current time
        assertTrue(lastHeartbeat >= beforeInitialize);
        assertTrue(lastHeartbeat <= afterInitialize);

        // Verify expiresAt is lastHeartbeat + 60 seconds
        assertEquals(60, expiresAt - lastHeartbeat);
    }

    @Test
    void testHeartbeat_UpdatesAllRooms() {
        // Given
        service.initialize();
        reset(dynamoDbClient);

        // When
        service.heartbeat();

        // Then
        verify(dynamoDbClient, times(3)).putItem(any(PutItemRequest.class));
    }

    @Test
    void testHeartbeat_HandlesExceptions() {
        // Given
        service.initialize();
        reset(dynamoDbClient);

        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

        // When/Then - should not throw exception
        assertDoesNotThrow(() -> service.heartbeat());

        // Should attempt to register all 3 rooms even if some fail
        verify(dynamoDbClient, times(3)).putItem(any(PutItemRequest.class));
    }

    @Test
    void testRegisterRoom_PartialFailure() {
        // Given
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(null)  // First room succeeds
                .thenThrow(DynamoDbException.builder().message("Error").build())  // Second room fails
                .thenReturn(null);  // Third room succeeds

        // When
        service.initialize();

        // Then - should continue registering even after failure
        verify(dynamoDbClient, times(3)).putItem(any(PutItemRequest.class));
    }

    @Test
    void testInitialize_WithDifferentMaxRooms() {
        // Given
        ReflectionTestUtils.setField(service, "maxRooms", 10);

        // When
        service.initialize();

        // Then
        verify(dynamoDbClient, times(10)).putItem(any(PutItemRequest.class));
    }

    @Test
    void testInitialize_WithDifferentPort() {
        // Given
        ReflectionTestUtils.setField(service, "port", 9090);

        // When
        service.initialize();

        // Then
        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient, atLeastOnce()).putItem(captor.capture());

        String endpoint = captor.getValue().item().get("endpoint").s();
        assertTrue(endpoint.contains(":9090/"));
    }

    @Test
    void testInitialize_EnvironmentUtilsFailure() {
        // Given
        when(environmentUtils.getHostname()).thenThrow(new RuntimeException("Cannot get hostname"));

        // When/Then
        assertThrows(RuntimeException.class, () -> service.initialize());

        // Should not register any rooms if initialization fails
        verify(dynamoDbClient, never()).putItem(any(PutItemRequest.class));
    }

    @Test
    void testHeartbeat_UpdatesTimestamp() {
        // Given
        service.initialize();
        reset(dynamoDbClient);

        long beforeHeartbeat = Instant.now().getEpochSecond();

        // When
        service.heartbeat();

        long afterHeartbeat = Instant.now().getEpochSecond();

        // Then
        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient, atLeastOnce()).putItem(captor.capture());

        PutItemRequest request = captor.getValue();
        long lastHeartbeat = Long.parseLong(request.item().get("lastHeartbeat").n());

        assertTrue(lastHeartbeat >= beforeHeartbeat);
        assertTrue(lastHeartbeat <= afterHeartbeat);
    }

    @Test
    void testRegisterAllRooms_ContainsRequiredFields() {
        // When
        service.initialize();

        // Then
        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient, times(3)).putItem(captor.capture());

        for (PutItemRequest request : captor.getAllValues()) {
            Map<String, AttributeValue> item = request.item();

            // Verify all required fields are present
            assertTrue(item.containsKey("roomId"));
            assertTrue(item.containsKey("consumerId"));
            assertTrue(item.containsKey("endpoint"));
            assertTrue(item.containsKey("lastHeartbeat"));
            assertTrue(item.containsKey("expiresAt"));

            // Verify correct types
            assertNotNull(item.get("roomId").s());
            assertNotNull(item.get("consumerId").s());
            assertNotNull(item.get("endpoint").s());
            assertNotNull(item.get("lastHeartbeat").n());
            assertNotNull(item.get("expiresAt").n());
        }
    }
}
