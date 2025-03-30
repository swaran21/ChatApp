package com.chat.model;

import lombok.Data;

@Data
public class ChatMessage {
    private Long chatId;
    private String sender;
    private String content;
}
