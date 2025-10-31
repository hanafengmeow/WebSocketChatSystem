package edu.northeastern.hanafeng.chatsystem.consumer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@Profile("consumer")
public class StompServerConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${websocketchat.consumer.api.broadcast.endpoint}")
    private String stompEndpoint;

    @Value("${websocketchat.consumer.api.broadcast.topic-prefix}")
    private String topicPrefix;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple broker for configured topic prefix (in-memory broker)
        config.enableSimpleBroker(topicPrefix);

        log.info("STOMP message broker configured with broker prefix: {}", topicPrefix);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Native WebSocket endpoint for internal Java clients (no SockJS needed)
        registry.addEndpoint(stompEndpoint)
                .setAllowedOrigins("*");

        log.info("STOMP WebSocket endpoint registered at: {}", stompEndpoint);
    }
}
