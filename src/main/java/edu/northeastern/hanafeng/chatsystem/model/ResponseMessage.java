package edu.northeastern.hanafeng.chatsystem.model;

import lombok.Data;

import java.time.Instant;

@Data
public class ResponseMessage {
    private ChatMessage echo;
    private Instant serverTimestamp;
    private String status;
    private String error;
}
