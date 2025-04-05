package com.chat.model;

import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Document(collection = "messages")
public class ChatMessage {
    @Id
    private String id;
    private Long chatId;
    private String sender;
    private String type; // "TEXT", "FILE_URL"
    private String content; // Holds text or file URL
    private LocalDateTime timestamp;

    private String fileName;
    private String fileType;

    public ChatMessage(Long chatId, String sender, String content, String type) {
        if (!"TEXT".equals(type)) { throw new IllegalArgumentException("Use specific constructor for type: " + type); }
        this.chatId = chatId;
        this.sender = sender;
        this.content = content;
        this.type = type;
        this.timestamp = LocalDateTime.now();
    }

    public ChatMessage(Long chatId, String sender, String fileUrl, String fileName, String fileType, String type) {
        if (!"FILE_URL".equals(type)) { throw new IllegalArgumentException("Use specific constructor for type: " + type); }
        if (fileUrl == null || fileName == null || fileType == null) { throw new IllegalArgumentException("File URL, Name, and Type are required for FILE_URL message."); }
        this.chatId = chatId;
        this.sender = sender;
        this.content = fileUrl;
        this.fileName = fileName;
        this.fileType = fileType;
        this.type = type;
        this.timestamp = LocalDateTime.now();
    }
}