package edu.northeastern.hanafeng.chatsystem.server.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("server")
public class ConsumerDiscoveryService {

    private final DynamoDbClient dynamoDbClient;

    @Value("${websocketchat.consumer.registry.table-name}")
    private String tableName;

    /**
     * Query DynamoDB registry to find the consumer endpoint for a given room.
     * Returns null if no consumer is registered for this room.
     */
    public String findConsumerEndpoint(String roomId) {
        try {
            Map<String, AttributeValue> expressionValues = new HashMap<>();
            expressionValues.put(":roomId", AttributeValue.builder().s(roomId).build());

            QueryRequest request = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression("roomId = :roomId")
                    .expressionAttributeValues(expressionValues)
                    .limit(1)
                    .build();

            QueryResponse response = dynamoDbClient.query(request);

            if (response.items().isEmpty()) {
                log.debug("No consumer found for room {}", roomId);
                return null;
            }

            AttributeValue endpointValue = response.items().get(0).get("endpoint");
            if (endpointValue == null) {
                log.warn("Consumer found for room {} but endpoint is null", roomId);
                return null;
            }

            String endpoint = endpointValue.s();
            log.info("Found consumer endpoint for room {}: {}", roomId, endpoint);
            return endpoint;

        } catch (Exception e) {
            log.error("Failed to query consumer registry for room {}: {}", roomId, e.getMessage(), e);
            return null;
        }
    }
}
