package edu.northeastern.hanafeng.chatsystem.server.config;

import edu.northeastern.hanafeng.chatsystem.server.handlers.BroadcastStompEventHandler;
import edu.northeastern.hanafeng.chatsystem.server.services.StompSubscriptionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Slf4j
@RequiredArgsConstructor
@Profile("server")
public class MessageBroadcasterConfig {

    private final StompSubscriptionService stompSubscriptionService;
    private final BroadcastStompEventHandler broadcastStompEventHandler;

    @PostConstruct
    public void configureMessageBroadcaster() {
        log.info("Configuring message broadcaster");

        stompSubscriptionService
                .registerHandler(broadcastStompEventHandler);

        log.info("Message broadcaster configured");
    }
}
