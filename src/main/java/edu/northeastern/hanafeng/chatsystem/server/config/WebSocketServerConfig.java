package edu.northeastern.hanafeng.chatsystem.server.config;

import edu.northeastern.hanafeng.chatsystem.server.handlers.ChatWebSocketEventHandler;
import edu.northeastern.hanafeng.chatsystem.server.handlers.RoomIdInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@Profile("server")
public class WebSocketServerConfig implements WebSocketConfigurer {

    private final ChatWebSocketEventHandler chatWebSocketEventHandler;
    private final RoomIdInterceptor roomIdInterceptor;

    @Value("${websocketchat.server.api.chat.endpoint}")
    private String chatEndpoint;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketEventHandler, chatEndpoint)
                .addInterceptors(roomIdInterceptor)
                .setAllowedOrigins("*");
    }
}
