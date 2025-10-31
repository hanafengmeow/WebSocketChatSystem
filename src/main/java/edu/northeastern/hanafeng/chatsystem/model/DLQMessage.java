package edu.northeastern.hanafeng.chatsystem.model;

import lombok.Data;

import java.time.Instant;

@Data
public class DLQMessage {
    private String roomId;
    private ChatMessage originalMessage;
    private String error;
    private Instant timestamp;
}
