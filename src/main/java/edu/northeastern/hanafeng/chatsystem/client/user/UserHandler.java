package edu.northeastern.hanafeng.chatsystem.client.user;

import edu.northeastern.hanafeng.chatsystem.client.support.MessageTextPool;
import edu.northeastern.hanafeng.chatsystem.model.ChatMessage;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * This class handles the lifecycle of a single user message sequence
 * It ensures the message order
 * Thread-safe: getNextMessageToSend() and handleEchoback() are synchronized
 */
public class UserHandler {

    public enum State {
        INIT,                    // Initial state, ready to send JOIN
        JOIN_SENT,              // JOIN message sent, waiting for server echoback
        JOINED,                 // JOIN confirmed by server, ready to send TEXT messages
        TEXTING,                // Currently sending TEXT messages
        ALL_TEXTS_CONFIRMED,    // All TEXT messages have been confirmed by server
        LEAVE_SENT,             // LEAVE message sent, waiting for server echoback
        DONE                    // User lifecycle complete
    }

    private final int userId;
    private final int roomId;
    private final String username;
    private State currentState;
    private final int totalTextMessages;
    private int textMessagesSent;

    private final Set<String> pendingTextConfirmations;
    private final ThreadLocalRandom random;

    private final UserHandlerManager manager;

    public UserHandler(int userId, int roomId, int totalTextMessages, UserHandlerManager manager) {
        this.userId = userId;
        this.roomId = roomId;
        this.totalTextMessages = totalTextMessages;
        this.random = ThreadLocalRandom.current();
        this.username = generateUsername();
        this.currentState = State.INIT;
        this.textMessagesSent = 0;
        this.pendingTextConfirmations = Collections.synchronizedSet(new HashSet<>());
        this.manager = manager;
    }

    public synchronized ChatMessage getNextMessageToSend() {

        ChatMessage message = new ChatMessage();

        message.setUserId(String.valueOf(userId));
        message.setRoomId(String.valueOf(roomId));
        message.setUsername(username);
        message.setTimestamp(Instant.now());

        String messageId = java.util.UUID.randomUUID().toString();
        message.setMessageId(messageId);

        switch (currentState) {
            case INIT:
                message.setMessageType("JOIN");
                message.setMessage("JOIN");
                changeState(State.JOIN_SENT);
                break;
            
            case JOINED:
                message.setMessageType("TEXT");
                message.setMessage(MessageTextPool.POOL[random.nextInt(MessageTextPool.POOL.length)]);
                pendingTextConfirmations.add(messageId);
                textMessagesSent++;
                changeState(State.TEXTING);
                break;

            case TEXTING:
                if (textMessagesSent >= totalTextMessages) {
                    throw new IllegalStateException("Cannot send more TEXT messages - already sent " + textMessagesSent + "/" + totalTextMessages);
                }

                message.setMessageType("TEXT");
                message.setMessage(MessageTextPool.POOL[random.nextInt(MessageTextPool.POOL.length)]);
                pendingTextConfirmations.add(messageId);
                textMessagesSent++;
                break;

            case ALL_TEXTS_CONFIRMED:
                message.setMessageType("LEAVE");
                message.setMessage("LEAVE");
                changeState(State.LEAVE_SENT);
                break;
            
            default:
                throw new IllegalStateException("Cannot send message in state: " + currentState);
        }
        
        return message;
    }

    public synchronized void handleEchoback(String messageType, String messageId) {
        switch (messageType) {
            case "JOIN":
                if (currentState == State.JOIN_SENT) {
                    changeState(State.JOINED);
                }
                break;

            case "TEXT":
                pendingTextConfirmations.remove(messageId);

                if (textMessagesSent >= totalTextMessages && pendingTextConfirmations.isEmpty()) {
                    if (currentState == State.TEXTING) {
                        changeState(State.ALL_TEXTS_CONFIRMED);
                    }
                }
                break;

            case "LEAVE":
                if (currentState == State.LEAVE_SENT) {
                    changeState(State.DONE);
                }
                break;
        }
    }

    private void changeState(State newState) {
        State oldState = this.currentState;
        this.currentState = newState;
        manager.notifyStateChange(userId, oldState, newState);
    }

    public boolean isComplete() {
        return currentState == State.DONE;
    }

    public boolean hasMessageToSend() {
        if (currentState == State.TEXTING) {
            return textMessagesSent < totalTextMessages;
        }

        return currentState == State.INIT ||
               currentState == State.JOINED ||
               currentState == State.ALL_TEXTS_CONFIRMED;
    }

    @SuppressWarnings("unused")
    public State getCurrentState() {
        return currentState;
    }

    @SuppressWarnings("unused")
    public boolean allTextMessagesConfirmed() {
        return pendingTextConfirmations.isEmpty() && textMessagesSent >= totalTextMessages;
    }

    private String generateUsername() {
        StringBuilder stringBuilder = new StringBuilder();
        
        for (int i = 0; i < 3; i++) {
            char randomLetter = (char) ('A' + random.nextInt(26));
            stringBuilder.append(randomLetter);
        }
        stringBuilder.append(userId);
        
        return stringBuilder.toString();
    }

    @SuppressWarnings("unused")
    public int getUserId() {
        return userId;
    }

    @SuppressWarnings("unused")
    public int getRoomId() {
        return roomId;
    }

    public int getPendingTextConfirmationCount() {
        return pendingTextConfirmations.size();
    }
}