package com.chat.model;

import jakarta.persistence.Id;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "messages")
public class ChatMessage {
    @Id
    private String id;
    private Long chatId;
    private String sender;
    private String content;
    private LocalDateTime timestamp;

    public ChatMessage() {
    }

    // New constructor that takes chatId, sender, and content.
    // It sets the timestamp to the current time.
    public ChatMessage(Long chatId, String sender, String content) {
        this.chatId = chatId;
        this.sender = sender;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }

}
