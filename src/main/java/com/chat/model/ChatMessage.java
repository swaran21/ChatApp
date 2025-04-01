package com.chat.model;

import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "messages")
public class ChatMessage {
    @Id
    private String id;
    private Long chatId;
    private String sender;
    private String type;
    private String content;
    private byte[] audioData;
    private String audioMimeType;
    private LocalDateTime timestamp;

    public ChatMessage() {
    }

    // New constructor that takes chatId, sender, and content.
    // It sets the timestamp to the current time.
    public ChatMessage(Long chatId, String sender, String content,String type) {
        this.chatId = chatId;
        this.sender = sender;
        this.content = content;
        this.type = type;
        this.timestamp = LocalDateTime.now();
    }

    //voice message
    public ChatMessage(Long chatId, String sender, byte[] audioData, String audioMimeType, String type) {
        this.chatId = chatId;
        this.sender = sender;
        this.audioData = audioData;
        this.audioMimeType = audioMimeType;
        this.type = type; // Should be "VOICE"
        this.timestamp = LocalDateTime.now();
    }

}
