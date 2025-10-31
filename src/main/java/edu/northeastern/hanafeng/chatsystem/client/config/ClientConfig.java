package edu.northeastern.hanafeng.chatsystem.client.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@ConfigurationProperties(prefix = "client")
@Validated
public class ClientConfig {

    private String wsBase;

    @Min(value = 1, message = "numUsers must be at least 1")
    @Max(value = 1000000, message = "numUsers cannot exceed 1,000,000")
    private int numUsers;

    @Min(value = 1, message = "numRooms must be at least 1")
    @Max(value = 20, message = "numRooms cannot exceed 20")
    private int numRooms;

    @Min(value = 1, message = "totalMessages must be at least 1")
    private long totalMessages;

    @Min(value = 1, message = "senderThreads must be at least 1")
    @Max(value = 5000, message = "senderThreads cannot exceed 5000")
    private int senderThreads;

    @Min(value = 1, message = "numProducerThreads must be at least 1")
    @Max(value = 1000, message = "numProducerThreads cannot exceed 1000")
    private int numProducerThreads = 1;

    @Min(value = 100, message = "messageQueueCapacity must be at least 100")
    @Max(value = 100000, message = "messageQueueCapacity cannot exceed 100,000")
    private int messageQueueCapacity = 5000;

    @Min(value = 0, message = "senderTaskQueueCapacity must be at least 0")
    @Max(value = 100000, message = "senderTaskQueueCapacity cannot exceed 100,000")
    private int senderTaskQueueCapacity = 10000;

    @Min(value = 1, message = "dispatcherThreads must be at least 1")
    @Max(value = 50, message = "dispatcherThreads cannot exceed 50")
    private int dispatcherThreads = 5;

    @Min(value = 1, message = "connectTimeoutSeconds must be at least 1")
    @Max(value = 120, message = "connectTimeoutSeconds cannot exceed 120")
    private int connectTimeoutSeconds;

    @Min(value = 1000, message = "ackTimeoutMs must be at least 1000")
    @Max(value = 300000, message = "ackTimeoutMs cannot exceed 300,000")
    private long ackTimeoutMs;

    @Min(value = 0, message = "pingIntervalSeconds must be >= 0")
    @Max(value = 600, message = "pingIntervalSeconds cannot exceed 600")
    private int pingIntervalSeconds = 50;
}