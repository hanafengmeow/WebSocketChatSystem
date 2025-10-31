package edu.northeastern.hanafeng.chatsystem.client.runner;

import edu.northeastern.hanafeng.chatsystem.client.config.ClientConfig;
import edu.northeastern.hanafeng.chatsystem.client.messagehandle.MessageDispatcher;
import edu.northeastern.hanafeng.chatsystem.client.messagehandle.MessageSequenceGenerator;
import edu.northeastern.hanafeng.chatsystem.client.metrics.ClientMetricsAggregator;
import edu.northeastern.hanafeng.chatsystem.client.transport.WebSocketClientManager;
import edu.northeastern.hanafeng.chatsystem.client.user.UserHandlerManager;
import edu.northeastern.hanafeng.chatsystem.client.support.UserEventLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Main client execution coordinator.
 * Implements CommandLineRunner for automatic execution after Spring context initialization.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientRunner implements CommandLineRunner {

    // All dependencies injected by Spring
    private final ClientConfig clientConfig;
    private final UserHandlerManager userHandlerManager;
    private final WebSocketClientManager wsClientManager;
    private final MessageSequenceGenerator messageSequenceGenerator;
    private final MessageDispatcher messageDispatcher;
    private final UserEventLogger userEventLogger;
    private final ClientMetricsAggregator metricsAggregator;

    @Override
    public void run(String... args) throws Exception {
        log.info("=== Client Starting ===");
        userEventLogger.initialize();
        long startTime = System.currentTimeMillis();
        initializeUsers();
        preCreateAllConnections();
        startDispatcher();
        startProducer();
        waitForCompletion();
        
        long runtime = (System.currentTimeMillis() - startTime) / 1000;
        log.info("Client runtime: {}s", runtime);
        wsClientManager.setRuntimeSeconds(runtime);
        metricsAggregator.flush();
        userEventLogger.shutdown();

        printStatisticsAndShutdown();
        
        log.info("=== Client Finished ===");
    }

    /**
     * Initialize all user handlers with calculated message distribution.
     */
    private void initializeUsers() {
        long totalMessagesNeeded = clientConfig.getTotalMessages();
        int numUsers = clientConfig.getNumUsers();
        int numRooms = clientConfig.getNumRooms();
        
        long messagesPerUser = totalMessagesNeeded / numUsers;
        int textMessagesPerUser = (int) (messagesPerUser - 2);
        if (textMessagesPerUser < 0) {
            textMessagesPerUser = 0;
        }
        
        long actualTotal = (long) numUsers * (textMessagesPerUser + 2);
        long leftover = totalMessagesNeeded - actualTotal;
        
        log.info("=== Message Distribution ===");
        log.info("Total Users: {}", numUsers);
        log.info("Total Rooms: {}", numRooms);
        log.info("Total Messages: {}", totalMessagesNeeded);
        log.info("Text Messages per User: {}", textMessagesPerUser);
        log.info("Leftover: {}", leftover);
        log.info("============================");
        
        userHandlerManager.initializeUsers(numUsers, numRooms, textMessagesPerUser, leftover);
    }

    /**
     * Pre-create all WebSocket connections in parallel before sending messages.
     * This avoids lazy initialization bottleneck during message sending.
     */
    private void preCreateAllConnections() throws Exception {
        int numRooms = clientConfig.getNumRooms();
        
        log.info("=== Pre-creating WebSocket Connections ===");
        log.info("Creating {} connections in parallel...", numRooms);
        
        long startTime = System.currentTimeMillis();
        
        // Create all connections in parallel using CompletableFuture
        CompletableFuture<?>[] futures = IntStream.rangeClosed(1, numRooms)
            .mapToObj(roomId -> CompletableFuture.runAsync(() -> {
                try {
                    wsClientManager.getConnection(roomId);
                    log.debug("Connection for room {} ready", roomId);
                } catch (Exception e) {
                    log.error("Failed to create connection for room {}", roomId, e);
                }
            }))
            .toArray(CompletableFuture[]::new);
        
        // Wait for all connections to complete
        CompletableFuture.allOf(futures).get();
        
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("All {} connections created in {}ms", numRooms, elapsed);
        log.info("==========================================");
    }

    /**
     * Start message dispatcher threads.
     */
    private void startDispatcher() {
        messageDispatcher.start();
        log.info("MessageDispatcher started");
    }

    /**
     * Start message producer thread.
     */
    private void startProducer() {
        messageSequenceGenerator.resetState();
        int producerThreads = clientConfig.getNumProducerThreads();
    
        for (int i = 0; i < producerThreads; i++) {
            messageSequenceGenerator.startProducingAsync();
        }
    
        log.info("Started {} producer worker(s)", producerThreads);
        log.info("=== Client Running ===");
    }

    /**
     * Wait for all messages to be generated, sent, and confirmed.
     */
    private void waitForCompletion() throws InterruptedException {
        log.info("Waiting for message generation and sending to complete...");
        
        long lastLogTime = System.currentTimeMillis();
        long lastMessageLogTime = System.currentTimeMillis(); 
        while (!userHandlerManager.areAllUsersComplete()) {
            TimeUnit.MILLISECONDS.sleep(500);
            
            long now = System.currentTimeMillis();
            if (now - lastLogTime > 5000) {
                log.info("Progress: Completed users={}/{}",
                        userHandlerManager.getCompletedUserCount(),
                        userHandlerManager.getTotalUsers());
                lastLogTime = now;
            }

            if (now - lastMessageLogTime > 10000) {
                long totalSent = wsClientManager.getTotalSentCount();
                log.info("Messages sent so far: {}", totalSent);
                lastMessageLogTime = now;
            }
        }
        
        // Additional wait for async sends to complete
        log.info("All messages generated, waiting for async sends to complete...");
        TimeUnit.SECONDS.sleep(5);
    }

    /**
     * Print final statistics and cleanup resources.
     */
    private void printStatisticsAndShutdown() {
        wsClientManager.printFinalStatistics();
        wsClientManager.closeAllConnections();
    }
}