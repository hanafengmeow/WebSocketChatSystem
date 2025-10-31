package edu.northeastern.hanafeng.chatsystem.server.components;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.hanafeng.chatsystem.common.services.CloudWatchMetricsService;
import edu.northeastern.hanafeng.chatsystem.model.ChatMessage;
import edu.northeastern.hanafeng.chatsystem.server.services.StompSubscriptionService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
@RequiredArgsConstructor
@Profile("server")
public class ClientSessionManager {

    private final StompSubscriptionService stompSubscriptionService;
    private final ObjectMapper objectMapper;
    private final CloudWatchMetricsService metricsService;

    // Track WebSocket client sessions per room
    private final Map<String, CopyOnWriteArrayList<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    /**
     * Add a client session to a room
     */
    public void addSession(String roomId, WebSocketSession session) {
        CopyOnWriteArrayList<WebSocketSession> sessions = roomSessions.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>());
        boolean isFirstClient = sessions.isEmpty();
        sessions.add(session);

        log.info("Client connected to room: {}, session: {}", roomId, session.getId());

        // Subscribe to consumer STOMP topic if this is the first client in the room
        if (isFirstClient) {
            log.info("First client in room {}, subscribing to consumer STOMP", roomId);
            stompSubscriptionService.subscribe(roomId);
        }
    }

    /**
     * Remove a client session from a room
     */
    public void removeSession(String roomId, WebSocketSession session) {
        CopyOnWriteArrayList<WebSocketSession> sessions = roomSessions.get(roomId);

        if (sessions == null) {
            log.warn("Room {} does not exist! Cannot remove session: {}", roomId, session.getId());
            return;
        }

        if (!sessions.contains(session)) {
            log.warn("Session {} not found in room {}", session.getId(), roomId);
            return;
        }

        sessions.remove(session);
        log.info("Client disconnected from room: {}, session: {}", roomId, session.getId());

        if (sessions.isEmpty()) {
            roomSessions.remove(roomId);
            // Unsubscribe from consumer STOMP when last client leaves
            log.info("Last client left room {}, unsubscribing from consumer STOMP", roomId);
            stompSubscriptionService.unsubscribe(roomId);
        }
    }

    /**
     * Broadcast a message from the consumer to all WebSocket clients in a room
     * This method is async to avoid blocking the STOMP message handler
     */
    @Async
    public void broadcastToRoom(String roomId, ChatMessage chatMessage) {
        CopyOnWriteArrayList<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("No clients connected to room {} for broadcast", roomId);
            return;
        }

        try {
            String messageJson = objectMapper.writeValueAsString(chatMessage);
            TextMessage textMessage = new TextMessage(messageJson);

            log.info("Broadcasting to {} clients in room {}, JSON: {}", sessions.size(), roomId, messageJson);

            int successCount = 0;
            int failureCount = 0;

            for (WebSocketSession session : sessions) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(textMessage);
                        successCount++;
                    } else {
                        log.warn("Session {} is closed, cannot broadcast message", session.getId());
                        failureCount++;
                    }
                } catch (Exception e) {
                    log.error("Failed to broadcast message to session {}: {}", session.getId(), e.getMessage(), e);
                    failureCount++;
                }
            }

            log.info("Broadcast complete for room {}: {} succeeded, {} failed", roomId, successCount, failureCount);

            // Record CloudWatch metrics
            if (successCount > 0) {
                metricsService.recordMetric("BroadcastSuccess", successCount);
            }
            if (failureCount > 0) {
                metricsService.recordMetric("BroadcastFailure", failureCount);
            }
        } catch (Exception e) {
            log.error("Failed to serialize message for broadcast to room {}: {}", roomId, e.getMessage(), e);
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up ClientSessionManager");

        // Collect all room IDs to avoid concurrent modification
        List<String> roomIds = new ArrayList<>(roomSessions.keySet());

        // Unsubscribe from all rooms before closing sessions
        for (String roomId : roomIds) {
            try {
                stompSubscriptionService.unsubscribe(roomId);
                log.debug("Unsubscribed from room {} during cleanup", roomId);
            } catch (Exception e) {
                log.error("Failed to unsubscribe from room {} during cleanup: {}", roomId, e.getMessage(), e);
            }
        }

        // Close all WebSocket sessions
        for (String roomId : roomIds) {
            CopyOnWriteArrayList<WebSocketSession> sessions = roomSessions.get(roomId);
            if (sessions != null) {
                for (WebSocketSession session : sessions) {
                    try {
                        if (session.isOpen()) {
                            session.close();
                            log.debug("Closed WebSocket session {} in room {}", session.getId(), roomId);
                        }
                    } catch (Exception e) {
                        log.error("Failed to close session {}: {}", session.getId(), e.getMessage(), e);
                    }
                }
            }
        }

        // Clear all room sessions
        roomSessions.clear();

        log.info("ClientSessionManager cleanup complete");
    }

}
