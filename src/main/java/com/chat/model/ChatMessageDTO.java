package com.chat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    private String id;
    private Long chatId;
    private String sender;
    private String type;
    private String content; // Will hold Base64 audio for VOICE type, text for TEXT, URL for FILE_URL
    private String audioMimeType; // Include mime type for receiver
    private LocalDateTime timestamp;

    // Static factory method to convert from Entity to DTO
    public static ChatMessageDTO fromEntity(ChatMessage entity) {
        String dtoContent = entity.getContent(); // Default to text content

        if ("VOICE".equals(entity.getType()) && entity.getAudioData() != null) {
            // Encode byte[] to Base64 for sending over WebSocket
            dtoContent = java.util.Base64.getEncoder().encodeToString(entity.getAudioData());
        }

        return new ChatMessageDTO(
                entity.getId(),
                entity.getChatId(),
                entity.getSender(),
                entity.getType(),
                dtoContent, // Use encoded content for voice
                entity.getAudioMimeType(),
                entity.getTimestamp()
        );
    }
}