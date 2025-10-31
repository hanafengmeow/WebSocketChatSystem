package edu.northeastern.hanafeng.chatsystem.consumer.services;

import edu.northeastern.hanafeng.chatsystem.common.utils.EnvironmentUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("consumer")
public class ConsumerRegistryService {

    private final DynamoDbClient dynamoDbClient;
    private final EnvironmentUtils environmentUtils;

    @Value("${websocketchat.consumer.registry.table-name}")
    private String tableName;

    @Value("${server.port}")
    private int port;

    @Value("${websocketchat.backend.max-rooms}")
    private int maxRooms;

    @Value("${websocketchat.consumer.registry.ttl-seconds}")
    private int ttlSeconds;

    @Value("${websocketchat.consumer.api.broadcast.endpoint}")
    private String broadcastEndpoint;

    private String consumerId;
    private String endpoint;

    @PostConstruct
    public void initialize() {
        // Get instance metadata
        consumerId = environmentUtils.getHostname();

        // Get internal IP address and build WebSocket endpoint
        String ipAddress = environmentUtils.getInternalIpAddress();
        endpoint = "ws://" + ipAddress + ":" + port + broadcastEndpoint;

        log.info("Consumer initialized: id={}, endpoint={}", consumerId, endpoint);

        // Register immediately on startup
        registerAllRooms();
    }

    @Scheduled(fixedRateString = "${websocketchat.consumer.registry.heartbeat-interval:30000}")
    @Async
    public void heartbeat() {
        try {
            registerAllRooms();
            log.info("Heartbeat: Updated registration for {} rooms", maxRooms);
        } catch (Exception e) {
            log.error("Failed to send heartbeat: {}", e.getMessage(), e);
        }
    }

    /**
     * Register all rooms in parallel for better performance
     * Uses CompletableFuture.supplyAsync to parallelize DynamoDB writes
     */
    private void registerAllRooms() {
        long now = Instant.now().getEpochSecond();
        long expiresAt = now + ttlSeconds;

        // Register all rooms in parallel - each room in its own async task
        List<CompletableFuture<Void>> futures = IntStream.rangeClosed(1, maxRooms)
                .mapToObj(roomId -> CompletableFuture.runAsync(() ->
                    registerRoom(String.valueOf(roomId), now, expiresAt)))
                .toList();

        // Wait for all registrations to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * Register a single room to DynamoDB
     * Called by registerAllRooms() within CompletableFuture.runAsync() for parallel execution
     */
    private void registerRoom(String roomId, long lastHeartbeat, long expiresAt) {
        try {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("roomId", AttributeValue.builder().s(roomId).build());
            item.put("consumerId", AttributeValue.builder().s(consumerId).build());
            item.put("endpoint", AttributeValue.builder().s(endpoint).build());
            item.put("lastHeartbeat", AttributeValue.builder().n(String.valueOf(lastHeartbeat)).build());
            item.put("expiresAt", AttributeValue.builder().n(String.valueOf(expiresAt)).build());

            PutItemRequest request = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build();

            dynamoDbClient.putItem(request);
            log.debug("Registered room {} with endpoint {}", roomId, endpoint);

        } catch (Exception e) {
            log.error("Failed to register room {}: {}", roomId, e.getMessage(), e);
        }
    }
}
