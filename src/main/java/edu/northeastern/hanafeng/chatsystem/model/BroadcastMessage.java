package edu.northeastern.hanafeng.chatsystem.model;

import lombok.Data;

import java.time.Instant;

@Data
public class BroadcastMessage {
    private ChatMessage chatMessage;
    private Instant broadcastTimestamp;
    private String roomId;
}
