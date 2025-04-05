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
    private String content;


    private String fileName; // File name to display
    private String fileType; // Original MIME type

    private LocalDateTime timestamp;

    public static ChatMessageDTO fromEntity(ChatMessage entity) {
        if (entity == null) { return null; }

        String dtoContent = entity.getContent();
        String dtoFileName = null;
        String dtoFileType = null;

        switch (entity.getType()) {
            case "FILE_URL":
                dtoFileName = entity.getFileName();
                dtoFileType = entity.getFileType();
                break;

            case "TEXT":
            default:
                dtoFileName = null;
                dtoFileType = null;
                break;
        }

        return new ChatMessageDTO(
                entity.getId(),
                entity.getChatId(),
                entity.getSender(),
                entity.getType(),
                dtoContent,
                dtoFileName,
                dtoFileType,
                entity.getTimestamp()
        );
    }
}