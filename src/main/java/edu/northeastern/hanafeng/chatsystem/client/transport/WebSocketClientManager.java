package edu.northeastern.hanafeng.chatsystem.client.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.northeastern.hanafeng.chatsystem.client.config.ClientConfig;
import edu.northeastern.hanafeng.chatsystem.client.support.ClientConstants;
import edu.northeastern.hanafeng.chatsystem.client.user.UserHandlerManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket connection pool manager.
 * Spring-managed service for managing WebSocket connections to chat rooms.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketClientManager {

    // Dependencies injected by Spring
    private final UserHandlerManager userHandlerManager;
    private final ClientConfig clientConfig;
    private final ObjectMapper objectMapper;

    // Internal state
    private final Map<Integer, WebSocketClient> connectionPool = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicLong> sentMessagesPerRoom = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicLong> successMessagesPerRoom = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicLong> failedMessagesPerRoom = new ConcurrentHashMap<>();
    private volatile long runtimeSeconds;

    public void setRuntimeSeconds(long runtimeSeconds) {
        this.runtimeSeconds = runtimeSeconds;
    }

    /**
     * Get or create WebSocket connection for a room.
     * Auto-heal: if missing or closed, try to recreate.
     */
    public WebSocketClient getConnection(int roomId) {
        WebSocketClient existing = connectionPool.get(roomId);
        if (existing == null || !existing.isOpen()) {
            try {
                WebSocketClient fresh = createRoomConnection(roomId);
                connectionPool.put(roomId, fresh);
                return fresh;
            } catch (Exception e) {
                log.error("Failed to (re)create connection for room {}", roomId, e);
                return existing; // may be null; caller can retry
            }
        }
        return existing;
    }

    private WebSocketChatClient createRoomConnection(int roomId) throws Exception {
        String fullUrl = ClientConstants.buildRoomWebSocketUrl(
                clientConfig.getWsBase(),
                roomId
        );
        URI serverUri = new URI(fullUrl);

        WebSocketChatClient wsClient = new WebSocketChatClient(
                serverUri,
                roomId,
                userHandlerManager,
                objectMapper
        );

        // Connect (blocking with timeout)
        wsClient.connectBlocking(
                clientConfig.getConnectTimeoutSeconds() * 1000L,
                java.util.concurrent.TimeUnit.MILLISECONDS
        );

        wsClient.setConnectionLostTimeout(0);

        // // Low-frequency heartbeat: every N seconds send PING and expect PONG
        // // 0 disables the built-in lost-connection detection.
        // try {
        //     int interval = Math.max(0, clientConfig.getPingIntervalSeconds());
        //     wsClient.setConnectionLostTimeout(interval);
        // } catch (Throwable t) {
        //     // If the client does not support, just log and continue
        //     log.warn("setConnectionLostTimeout not supported or failed", t);
        // }

        log.info("WebSocket connection created for room {}", roomId);
        return wsClient;
    }

    /**
     * Record a message send attempt
     */
    public void recordSent(int roomId) {
        sentMessagesPerRoom.computeIfAbsent(roomId, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Record a successful message send
     */
    public void recordSuccess(int roomId) {
        successMessagesPerRoom.computeIfAbsent(roomId, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Record a failed message send
     */
    public void recordFailure(int roomId) {
        failedMessagesPerRoom.computeIfAbsent(roomId, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Print final statistics per room
     */
    public void printFinalStatistics() {
        log.info("\n=== Final Statistics by Room ===");

        long totalSent = 0;
        long totalSuccess = 0;
        long totalFailed = 0;

        for (Integer roomId : connectionPool.keySet()) {
            long sent = sentMessagesPerRoom.getOrDefault(roomId, new AtomicLong(0)).get();
            long success = successMessagesPerRoom.getOrDefault(roomId, new AtomicLong(0)).get();
            long failed = failedMessagesPerRoom.getOrDefault(roomId, new AtomicLong(0)).get();

            log.info("Room {}: Sent={}, Success={}, Failed={}", roomId, sent, success, failed);

            totalSent += sent;
            totalSuccess += success;
            totalFailed += failed;
        }

        log.info("--------------------------------");
        log.info("Total: Sent={}, Success={}, Failed={}", totalSent, totalSuccess, totalFailed);
        if (runtimeSeconds > 0) {
            double throughput = (double) totalSuccess / runtimeSeconds;
            log.info("Total Runtime: {}s", runtimeSeconds);
            log.info("Throughput: {} msg/s", String.format("%.2f", throughput));
        } else {
            log.info("Total Runtime: not set (skipping throughput calculation)");
        }
        log.info("================================\n");
    }

    /**
     * Close all WebSocket connections gracefully.
     */
    public void closeAllConnections() {
        log.info("Closing all WebSocket connections...");
        for (WebSocketClient client : connectionPool.values()) {
            if (client != null && client.isOpen()) {
                try {
                    client.close();
                } catch (Exception e) {
                    log.error("Error closing connection", e);
                }
            }
        }
        connectionPool.clear();
        log.info("All connections closed");
    }

    @SuppressWarnings("unused")
    public int getConnectionPoolSize() {
        return connectionPool.size();
    }

    public long getTotalSentCount() {
        return sentMessagesPerRoom.values()
                .stream()
                .mapToLong(AtomicLong::get)
                .sum();
    }
}