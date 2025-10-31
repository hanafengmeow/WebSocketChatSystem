package edu.northeastern.hanafeng.chatsystem.server.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("server")
public class StompSubscriptionService {

    private final ConsumerDiscoveryService consumerDiscoveryService;
    private final WebSocketStompClient webSocketStompClient;

    @Value("${websocketchat.consumer.api.broadcast.topic-prefix}")
    private String topicPrefix;

    // Track one STOMP session per consumer endpoint (wsUrl -> session)
    private final Map<String, StompSession> consumerSessions = new ConcurrentHashMap<>();

    // Track subscribed rooms per consumer (wsUrl -> list of roomIds)
    private final Map<String, CopyOnWriteArrayList<String>> roomSubscriptions = new ConcurrentHashMap<>();

    // Track subscription objects for unsubscribing (roomId -> Subscription)
    private final Map<String, StompSession.Subscription> subscriptions = new ConcurrentHashMap<>();

    private StompSessionHandlerAdapter sessionHandler = null;

    public StompSubscriptionService registerHandler(StompSessionHandlerAdapter sessionHandler) {
        if (!this.roomSubscriptions.isEmpty()) {
            throw new IllegalStateException("Cannot register STOMP handler when there are existing subscriptions");
        }

        this.sessionHandler = sessionHandler;
        log.info("Registered STOMP session handler: {}", sessionHandler.getClass().getName());
        return this;
    }

    /**
     * Periodically check and reconnect to consumers
     */
    @Scheduled(fixedDelay = 5000)
    public void reconnectDisconnectedSessions() {
        for (Map.Entry<String, CopyOnWriteArrayList<String>> entry : roomSubscriptions.entrySet()) {
            String wsUrl = entry.getKey();
            CopyOnWriteArrayList<String> rooms = entry.getValue();

            if (rooms.isEmpty()) {
                continue;
            }

            // Check if session is connected
            StompSession session = consumerSessions.get(wsUrl);
            if (session == null || !session.isConnected()) {
                log.info("Reconnecting to {} (disconnected session, {} rooms affected)", wsUrl, rooms.size());
                reconnect(wsUrl, rooms);
            }
        }
    }

    /**
     * Reconnect to a consumer and resubscribe to all its rooms
     */
    private void reconnect(String wsUrl, CopyOnWriteArrayList<String> rooms) {
        try {
            // Try to connect
            StompSession session = connect(wsUrl);
            if (session == null || !session.isConnected()) {
                log.warn("Reconnection failed for {}, will retry in next cycle", wsUrl);
                return;
            }

            log.info("Reconnected to {}, resubscribing to {} rooms", wsUrl, rooms.size());

            // Resubscribe to all rooms
            for (String roomId : rooms) {
                try {
                    String topic = topicPrefix + "/" + roomId;
                    StompSession.Subscription subscription = session.subscribe(topic, sessionHandler);
                    subscriptions.put(roomId, subscription);
                    log.info("Resubscribed to room {}", roomId);
                } catch (Exception e) {
                    log.error("Failed to resubscribe to room {}: {}", roomId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Reconnection failed for {}: {}", wsUrl, e.getMessage());
        }
    }

    /**
     * Get existing STOMP session or create a new one for the room's consumer
     */
    private StompSession getOrCreateSession(String roomId) {
        // Find the consumer WebSocket URL for this room
        String wsUrl = consumerDiscoveryService.findConsumerEndpoint(roomId);
        if (wsUrl == null) {
            log.warn("Cannot get consumer session for room {}: no consumer endpoint found", roomId);
            return null;
        }

        // Check if we already have a connection to this consumer
        StompSession existingSession = consumerSessions.get(wsUrl);
        if (existingSession != null && existingSession.isConnected()) {
            log.debug("Reusing existing connection to consumer {}", wsUrl);
            return existingSession;
        }

        // Need to create a new connection
        log.info("Creating new connection to consumer {} for room {}", wsUrl, roomId);
        return connect(wsUrl);
    }

    /**
     * Create a new STOMP connection to the consumer WebSocket endpoint
     */
    private StompSession connect(String wsUrl) {
        try {
            log.info("Connecting to consumer STOMP endpoint: {}", wsUrl);
            StompSession session = webSocketStompClient.connectAsync(wsUrl, new WebSocketHttpHeaders(), sessionHandler).get();

            // Store the session
            consumerSessions.put(wsUrl, session);
            log.info("Connected to consumer: {}", wsUrl);

            return session;

        } catch (Exception e) {
            log.error("Failed to connect to consumer {}: {}", wsUrl, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Disconnect from a specific consumer endpoint
     */
    private void disconnect(String wsUrl) {
        if (wsUrl == null) {
            return;
        }

        StompSession session = consumerSessions.remove(wsUrl);
        if (session != null && session.isConnected()) {
            try {
                session.disconnect();
                log.info("Disconnected from consumer {} (no more rooms)", wsUrl);
            } catch (Exception e) {
                log.error("Failed to disconnect from consumer {}: {}", wsUrl, e.getMessage(), e);
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up STOMP subscription service");

        // Unsubscribe from all topics
        for (Map.Entry<String, StompSession.Subscription> entry : subscriptions.entrySet()) {
            try {
                entry.getValue().unsubscribe();
                log.debug("Unsubscribed from room {}", entry.getKey());
            } catch (Exception e) {
                log.error("Failed to unsubscribe from room {}: {}", entry.getKey(), e.getMessage(), e);
            }
        }
        subscriptions.clear();

        // Disconnect all STOMP sessions
        for (StompSession session : consumerSessions.values()) {
            try {
                if (session.isConnected()) {
                    session.disconnect();
                }
            } catch (Exception e) {
                log.error("Failed to disconnect session {}: {}", session.getSessionId(), e.getMessage(), e);
            }
        }
        consumerSessions.clear();

        // Clear room subscriptions tracking
        roomSubscriptions.clear();

        log.info("STOMP subscription service cleanup complete");
    }

    /**
     * Add a room to subscription tracking
     * @return true if room was added (new subscription), false if already exists
     */
    private boolean addRoom(String wsUrl, String roomId) {
        CopyOnWriteArrayList<String> rooms = roomSubscriptions.get(wsUrl);
        if (rooms != null && rooms.contains(roomId)) {
            return false;
        }
        roomSubscriptions.computeIfAbsent(wsUrl, k -> new CopyOnWriteArrayList<>()).add(roomId);
        return true;
    }

    /**
     * Remove a room from subscription tracking
     * @return true if no more rooms for this wsUrl (should disconnect), false otherwise
     */
    private boolean removeRoom(String wsUrl, String roomId) {
        CopyOnWriteArrayList<String> rooms = roomSubscriptions.get(wsUrl);
        if (rooms == null) {
            // No rooms tracked for this wsUrl, consider it empty
            return true;
        }

        rooms.remove(roomId);

        if (rooms.isEmpty()) {
            roomSubscriptions.remove(wsUrl);
            return true;
        }

        return false;
    }

    /**
     * Subscribe to a room's STOMP topic
     * This method is async to avoid blocking WebSocket connection establishment
     */
    @Async
    public void subscribe(String roomId) {
        // Check if handler is registered
        if (sessionHandler == null) {
            log.error("Cannot subscribe to room {}: STOMP handler not registered yet", roomId);
            return;
        }

        // Get the consumer endpoint for this room
        String wsUrl = consumerDiscoveryService.findConsumerEndpoint(roomId);
        if (wsUrl == null) {
            log.warn("Cannot subscribe to room {}: no consumer endpoint found", roomId);
            return;
        }

        // Check if already subscribed to this room
        if (!addRoom(wsUrl, roomId)) {
            log.debug("Already subscribed to room {}", roomId);
            return;
        }

        // Get or create the STOMP session
        StompSession session = getOrCreateSession(roomId);
        if (session == null || !session.isConnected()) {
            log.warn("Cannot subscribe to room {}: session not available, will retry", roomId);
            return;
        }

        String topic = topicPrefix + "/" + roomId;
        log.info("Subscribing to topic {} on session {}", topic, session.getSessionId());

        try {
            StompSession.Subscription subscription = session.subscribe(topic, sessionHandler);
            subscriptions.put(roomId, subscription);
            log.info("Subscribed to room {} on session {}", roomId, session.getSessionId());
        } catch (Exception e) {
            log.error("Failed to subscribe to room {} on session {}: {}",
                    roomId, session.getSessionId(), e.getMessage(), e);
        }
    }

    /**
     * Unsubscribe from a room's STOMP topic
     * Note: This method is NOT async when called from @PreDestroy to ensure cleanup completes
     */
    public void unsubscribe(String roomId) {
        // Get and remove the subscription
        StompSession.Subscription subscription = subscriptions.remove(roomId);
        if (subscription != null) {
            try {
                subscription.unsubscribe();
                log.info("Unsubscribed from room {}", roomId);
            } catch (Exception e) {
                log.error("Failed to unsubscribe from room {}: {}", roomId, e.getMessage(), e);
            }
        } else {
            log.debug("No active subscription found for room {}", roomId);
        }

        // Get the consumer endpoint for this room
        String wsUrl = consumerDiscoveryService.findConsumerEndpoint(roomId);
        if (wsUrl == null) {
            log.warn("Cannot find consumer endpoint for room {} during unsubscribe cleanup", roomId);
            return;
        }

        // Remove room from tracking and check if should disconnect
        boolean shouldDisconnect = removeRoom(wsUrl, roomId);
        if (shouldDisconnect) {
            disconnect(wsUrl);
        }
    }
}
