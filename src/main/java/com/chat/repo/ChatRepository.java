package com.chat.repo;

import com.chat.model.ChatModelCreation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ChatRepository extends JpaRepository<ChatModelCreation, Long> {

    List<ChatModelCreation> findByOwnerIdOrReceiverId(Long ownerId, Long receiverId);
    Optional<ChatModelCreation> findByChatId(Long chatId);
    void deleteByChatId(Long chatId);

    Optional<ChatModelCreation> findByOwnerIdAndReceiverId(Long ownerId, Long receiverId);
}
