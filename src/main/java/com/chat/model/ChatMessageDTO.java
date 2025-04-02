// src/main/java/com/chat/model/ChatMessageDTO.java
package com.chat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Base64; // Correct import

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    private String id;
    private Long chatId;
    private String sender;
    private String type; // "TEXT", "VOICE", "FILE_URL"
    private String content; // Holds text, Base64 audio, or file URL
    private String audioMimeType; // Only relevant for VOICE type

    // --- Added for File Uploads ---
    private String fileName; // File name to display
    private String fileType; // Original MIME type

    private LocalDateTime timestamp;


    // Static factory method to convert from Entity to DTO
    public static ChatMessageDTO fromEntity(ChatMessage entity) {
        if (entity == null) {
            return null;
        }

        String dtoContent = entity.getContent(); // Default to text content or URL
        String dtoFileName = entity.getFileName();
        String dtoFileType = entity.getFileType();
        String dtoAudioMimeType = entity.getAudioMimeType();

        // Handle VOICE type: Encode byte[] to Base64 for sending over WebSocket
        // Only encode if audioData exists and type is VOICE
        if ("VOICE".equals(entity.getType()) && entity.getAudioData() != null) {
            dtoContent = Base64.getEncoder().encodeToString(entity.getAudioData());
            // Clear file-specific fields if accidentally set on a voice message entity
            dtoFileName = null;
            dtoFileType = null;
        } else if ("FILE_URL".equals(entity.getType())) {
            // Ensure audio fields are null for file types
            dtoAudioMimeType = null;
        } else { // TEXT or other types
            // Ensure audio and file fields are null for text types
            dtoAudioMimeType = null;
            dtoFileName = null;
            dtoFileType = null;
        }


        return new ChatMessageDTO(
                entity.getId(),
                entity.getChatId(),
                entity.getSender(),
                entity.getType(),
                dtoContent,
                dtoAudioMimeType, // Will be null if not VOICE
                dtoFileName,      // Will be null if not FILE_URL
                dtoFileType,      // Will be null if not FILE_URL
                entity.getTimestamp()
        );
    }
}