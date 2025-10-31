package edu.northeastern.hanafeng.chatsystem.model;

import edu.northeastern.hanafeng.chatsystem.model.validation.ValidTimestamp;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class ChatMessage {
    @NotBlank(message = "userId missing")
    @Pattern(regexp = "^[1-9][0-9]{0,4}$|^100000$", message = "userId must be 1-100000")
    private String userId;

    @NotBlank(message = "roomId missing")
    @Pattern(regexp = "^[1-9]$|^1[0-9]$|^20$", message = "roomId must be 1-20 alphanumeric characters")
    private String roomId;

    @NotBlank(message = "messageId missing")
    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            message = "messageId must be a valid UUID")
    private String messageId;

    @NotBlank(message = "username missing")
    @Pattern(regexp = "^[A-Za-z0-9]{3,20}$", message = "username must be 3-20 alphanumeric characters")
    private String username;

    @NotBlank(message = "message missing")
    @Size(min = 1, max = 5000, message = "message must be 1-5000 characters")
    private String message;

    @NotNull(message = "timestamp missing")
    @ValidTimestamp
    private Instant timestamp;

    @NotBlank(message = "messageType missing")
    @Pattern(regexp = "TEXT|JOIN|LEAVE", message = "messageType must be TEXT, JOIN, or LEAVE")
    private String messageType;
}
