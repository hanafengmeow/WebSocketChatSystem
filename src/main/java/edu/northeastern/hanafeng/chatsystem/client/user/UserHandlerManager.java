package edu.northeastern.hanafeng.chatsystem.client.user;

import edu.northeastern.hanafeng.chatsystem.model.ChatMessage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * UserHandlerManager manages all user handlers in the system.
 * This is a Spring-managed component that coordinates message generation
 * across all simulated users.
 */
@Slf4j
@Component
public class UserHandlerManager {

    private final Map<Integer, UserHandler> userHandlers;
    private final Set<Integer> usersInInit;
    private final Set<Integer> usersInJoined;
    private final Set<Integer> usersInTexting;
    private final Set<Integer> usersInAllTextsConfirmed;

    private final ThreadLocalRandom random;

    @Getter
    private int totalUsers;

    public UserHandlerManager() {
        this.userHandlers = new ConcurrentHashMap<>();
        this.usersInInit = ConcurrentHashMap.newKeySet();
        this.usersInJoined = ConcurrentHashMap.newKeySet();
        this.usersInTexting = ConcurrentHashMap.newKeySet();
        this.usersInAllTextsConfirmed = ConcurrentHashMap.newKeySet();
        this.random = ThreadLocalRandom.current();
        this.totalUsers = 0;
    }

    /**
     * Initialize users with support for leftover message distribution.
     */
    public void initializeUsers(int totalUsers, int totalRooms, int totalTextMessages, long leftoverMessages) {
        this.totalUsers = totalUsers;

        for (int userId = 1; userId <= totalUsers; userId++) {
            int roomId = ((userId - 1) % totalRooms) + 1;

            int textMessagesForThisUser = totalTextMessages;
            if (userId <= leftoverMessages) {
                textMessagesForThisUser += 1;
            }

            UserHandler handler = new UserHandler(
                userId,
                roomId,
                textMessagesForThisUser,
                this
            );

            userHandlers.put(userId, handler);
            usersInInit.add(userId);
        }

        log.info("Initialized {} users across {} rooms", totalUsers, totalRooms);
    }

    /**
     * Get the next message to send from any eligible user.
     * Simple and reliable: directly sample from Sets.
     */
    public ChatMessage getNextMessage() {
        // Collect non-empty sets
        List<Set<Integer>> eligibleSets = new ArrayList<>(4);
        if (!usersInInit.isEmpty()) eligibleSets.add(usersInInit);
        if (!usersInJoined.isEmpty()) eligibleSets.add(usersInJoined);
        if (!usersInTexting.isEmpty()) eligibleSets.add(usersInTexting);
        if (!usersInAllTextsConfirmed.isEmpty()) eligibleSets.add(usersInAllTextsConfirmed);

        if (eligibleSets.isEmpty()) {
            return null;
        }

        // Try up to 5 times
        for (int attempt = 0; attempt < 5; attempt++) {
            // Pick a random set
            Set<Integer> selectedSet = eligibleSets.get(random.nextInt(eligibleSets.size()));
            
            // Convert to array for random access
            Integer[] users = selectedSet.toArray(new Integer[0]);
            if (users.length == 0) {
                continue;
            }
            
            // Pick random user
            Integer userId = users[random.nextInt(users.length)];
            
            UserHandler handler = userHandlers.get(userId);
            if (handler != null && handler.hasMessageToSend()) {
                ChatMessage msg = handler.getNextMessageToSend();
                if (msg != null) {
                    return msg;
                }
            }
        }

        return null;
    }

    public void notifyStateChange(int userId, UserHandler.State oldState, UserHandler.State newState) {
        removeFromStateIndex(userId, oldState);
        addToStateIndex(userId, newState);
    }

    private void removeFromStateIndex(int userId, UserHandler.State state) {
        switch (state) {
            case INIT: usersInInit.remove(userId); break;
            case JOINED: usersInJoined.remove(userId); break;
            case TEXTING: usersInTexting.remove(userId); break;
            case ALL_TEXTS_CONFIRMED: usersInAllTextsConfirmed.remove(userId); break;
            default: break;
        }
    }

    private void addToStateIndex(int userId, UserHandler.State state) {
        switch (state) {
            case INIT: usersInInit.add(userId); break;
            case JOINED: usersInJoined.add(userId); break;
            case TEXTING: usersInTexting.add(userId); break;
            case ALL_TEXTS_CONFIRMED: usersInAllTextsConfirmed.add(userId); break;
            default: break;
        }
    }

    public void handleEchoback(int userId, String messageType, String messageId) {
        UserHandler handler = userHandlers.get(userId);
        if (handler != null) {
            handler.handleEchoback(messageType, messageId);
        } else {
            log.warn("Received echoback for unknown userId: {}", userId);
        }
    }

    /**
     * Utility method: Get handler for specific user (for debugging/testing)
     */
    @SuppressWarnings("unused")
    public UserHandler getUserHandler(int userId) {
        return userHandlers.get(userId);
    }

    public int getCompletedUserCount() {
        return (int) userHandlers.values().stream()
            .filter(UserHandler::isComplete)
            .count();
    }

    public boolean areAllUsersComplete() {
        return getCompletedUserCount() == totalUsers;
    }

    /**
     * Utility method: Get total pending confirmations (for monitoring)
     */
    @SuppressWarnings("unused")
    public int getTotalPendingConfirmations() {
        return userHandlers.values().stream()
            .mapToInt(UserHandler::getPendingTextConfirmationCount)
            .sum();
    }
}