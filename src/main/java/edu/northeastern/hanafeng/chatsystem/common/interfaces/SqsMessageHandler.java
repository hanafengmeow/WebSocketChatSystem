package edu.northeastern.hanafeng.chatsystem.common.interfaces;

import edu.northeastern.hanafeng.chatsystem.model.ChatMessage;

/**
 * Interface for handling messages received from SQS.
 * Implementations define how to process messages for specific use cases.
 */
public interface SqsMessageHandler {

    /**
     * Handle a deserialized message received from SQS for a specific room.
     *
     * @param roomId The room identifier
     * @param chatMessage The deserialized chat message
     * @throws Exception if message processing fails
     */
    void handleMessage(String roomId, ChatMessage chatMessage) throws Exception;
}
