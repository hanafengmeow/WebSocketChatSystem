package edu.northeastern.hanafeng.chatsystem.server.handlers;

import edu.northeastern.hanafeng.chatsystem.model.BroadcastMessage;
import edu.northeastern.hanafeng.chatsystem.model.ChatMessage;
import edu.northeastern.hanafeng.chatsystem.server.components.ClientSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;

@Component
@Slf4j
@RequiredArgsConstructor
@Profile("server")
public class BroadcastStompEventHandler extends StompSessionHandlerAdapter {

    private final ClientSessionManager clientSessionManager;

    @Value("${websocketchat.consumer.api.broadcast.topic-prefix}")
    private String topicPrefix;

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        log.info("Connected to consumer STOMP endpoint, session: {}", session.getSessionId());
    }

    @Override
    public void handleException(StompSession session, StompCommand command,
                              StompHeaders headers, byte[] payload, Throwable exception) {
        log.error("STOMP exception on session {}: {}", session.getSessionId(), exception.getMessage(), exception);
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
        log.error("STOMP transport error on session {}: {}", session.getSessionId(), exception.getMessage(), exception);
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
        return BroadcastMessage.class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
        try {
            // Extract roomId from topic destination
            String destination = headers.getDestination();
            if (destination == null || !destination.startsWith(topicPrefix + "/")) {
                log.warn("Received message with invalid destination: {}", destination);
                return;
            }

            String roomId = destination.substring((topicPrefix + "/").length());

            // Cast to BroadcastMessage (Jackson already deserialized it)
            if (!(payload instanceof BroadcastMessage)) {
                log.error("Unexpected payload type: {}", payload != null ? payload.getClass() : "null");
                return;
            }

            BroadcastMessage broadcastMessage = (BroadcastMessage) payload;
            ChatMessage chatMessage = broadcastMessage.getChatMessage();

            clientSessionManager.broadcastToRoom(roomId, chatMessage);
            log.debug("Broadcast message to room {}: {}", roomId, chatMessage.getMessageId());
        } catch (Exception e) {
            log.error("Error handling broadcast frame: {}", e.getMessage(), e);
        }
    }
}
