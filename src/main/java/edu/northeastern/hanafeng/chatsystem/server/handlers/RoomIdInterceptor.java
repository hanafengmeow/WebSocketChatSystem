package edu.northeastern.hanafeng.chatsystem.server.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriTemplate;

import java.util.Map;

@Slf4j
@Component
@Profile("server")
public class RoomIdInterceptor implements HandshakeInterceptor {

    @Value("${websocketchat.server.api.chat.endpoint}")
    private String chatEndpoint;

    @Value("${websocketchat.backend.max-rooms}")
    private int maxRooms;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String path = request.getURI().getPath();
        log.info("WebSocket handshake attempt for path: {}", path);

        Map<String, String> uriVariables = new UriTemplate(chatEndpoint).match(path);
        String roomId = uriVariables.get("roomId");

        RoomIdValidator validator = new RoomIdValidator(maxRooms);
        if (validator.validate(roomId)) {
            log.info("WebSocket handshake accepted for roomId: {}", roomId);
            attributes.put("roomId", roomId);
            return true;
        } else {
            log.warn("WebSocket handshake rejected for invalid roomId: {}", roomId);
            attributes.put("roomId", null);
            return false; // Reject handshake
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("Error during WebSocket handshake: {}", exception.getMessage(), exception);
        } else {
            log.info("WebSocket handshake completed successfully");
        }
    }

    private static class RoomIdValidator {
        private final int maxRooms;

        private RoomIdValidator(int maxRooms) {
            this.maxRooms = maxRooms;
        }

        private boolean validate(String roomId) {
            return isNonEmpty(roomId)
                    && isNumeric(roomId)
                    && isWithinRange(Integer.parseInt(roomId));
        }

        private boolean isNonEmpty(String roomId) {
            return roomId != null && !roomId.isEmpty();
        }

        private boolean isNumeric(String roomId) {
            try {
                Integer.parseInt(roomId);
                return true;
            } catch (NumberFormatException e) {
                log.info("RoomId is not numeric: {}", roomId);
                return false;
            }
        }

        private boolean isWithinRange(int roomIdInt) {
            return roomIdInt > 0 && roomIdInt <= maxRooms;
        }
    }
}
