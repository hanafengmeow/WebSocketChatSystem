package edu.northeastern.hanafeng.chatsystem.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@Configuration
@Profile("server")
public class StompClientConfig {

    @Bean
    public WebSocketStompClient webSocketStompClient(ObjectMapper objectMapper) {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

        // Configure message converter with Spring's ObjectMapper (already has JavaTimeModule)
        MappingJackson2MessageConverter jacksonConverter = new MappingJackson2MessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(jacksonConverter);

        return stompClient;
    }
}
