package edu.northeastern.hanafeng.chatsystem.client.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.hanafeng.chatsystem.client.user.UserHandlerManager;
import edu.northeastern.hanafeng.chatsystem.model.ResponseMessage;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

/**
 * WebSocket client for a specific chat room.
 * Handles connection lifecycle and message receiving.
 */
@Slf4j
public class WebSocketChatClient extends WebSocketClient {
    
    private final int roomId;
    private final UserHandlerManager userHandlerManager;
    private final ObjectMapper objectMapper;

    public WebSocketChatClient(
            URI serverUri,
            int roomId,
            UserHandlerManager userHandlerManager,
            ObjectMapper objectMapper) {
        super(serverUri);
        this.roomId = roomId;
        this.userHandlerManager = userHandlerManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("Room {} connected successfully (status: {})", roomId, handshake.getHttpStatus());
    }

    @Override
    public void onMessage(String message) {
        try {
            ResponseMessage response = objectMapper.readValue(message, ResponseMessage.class);

            if (response.getEcho() != null) {
                int userId = Integer.parseInt(response.getEcho().getUserId());
                String messageType = response.getEcho().getMessageType();
                String messageId = response.getEcho().getMessageId();

                userHandlerManager.handleEchoback(userId, messageType, messageId);

                log.debug("Echoback received: Room={}, User={}, Type={}", roomId, userId, messageType);
            }
        } catch (Exception e) {
            log.warn("Failed to parse message from room {}: {}", roomId, e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.info("Room {} connection closed (code={}, reason={}, remote={})", 
                roomId, code, reason, remote);
    }

    @Override
    public void onError(Exception ex) {
        log.error("WebSocket error for room {}", roomId, ex);
    }

    public int getRoomId() {
        return roomId;
    }
}