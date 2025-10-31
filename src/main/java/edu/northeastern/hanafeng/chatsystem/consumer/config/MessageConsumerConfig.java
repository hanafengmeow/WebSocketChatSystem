package edu.northeastern.hanafeng.chatsystem.consumer.config;

import edu.northeastern.hanafeng.chatsystem.common.services.SqsPollingService;
import edu.northeastern.hanafeng.chatsystem.consumer.handlers.BroadcastSqsMessageHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for message consumer.
 * Registers handlers and starts the polling service.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
@Profile("consumer")
public class MessageConsumerConfig {

    private final SqsPollingService sqsPollingService;
    private final BroadcastSqsMessageHandler broadcastSqsMessageHandler;

    @PostConstruct
    public void configureMessageConsumer() {
        log.info("Configuring message consumer");

        // Register handler and start polling
        sqsPollingService
                .registerHandler(broadcastSqsMessageHandler)
                .start();

        log.info("Message consumer configured and started");
    }
}
