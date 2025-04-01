package com.chat.repo;

import com.chat.model.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ChatMessageRepo extends MongoRepository<ChatMessage, String> {
    List<ChatMessage> findByChatId(Long chatId);

    List<ChatMessage> findByChatIdOrderByTimestampAsc(Long chatId);
}
