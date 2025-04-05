package com.chat.service;

import com.chat.model.ChatModelCreation;
import com.chat.model.UserModel;
import com.chat.repo.ChatMessageRepo;
import com.chat.repo.ChatRepository;
import com.chat.repo.UserRepo;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException; // Import
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ChatService {

    private final ChatRepository chatRepository;
    private final UserRepo userRepo;
    private final ChatMessageRepo chatMessageRepo;

    @Autowired
    public ChatService(ChatRepository chatRepository, UserRepo userRepo, ChatMessageRepo chatMessageRepo) {
        this.chatRepository = chatRepository;
        this.userRepo = userRepo;
        this.chatMessageRepo = chatMessageRepo;
    }

    public ChatModelCreation createChat(ChatModelCreation chatModelCreation, Long ownerId) {
        if (ownerId == null) { // Basic check
            throw new IllegalArgumentException("Owner ID cannot be null.");
        }

        Optional<UserModel> receiverInfo = userRepo.findByUsername(chatModelCreation.getReceiverName());
        if (receiverInfo.isEmpty()) {
            throw new RuntimeException("Receiver User Not Found: " + chatModelCreation.getReceiverName());
        }

        Long receiverId = receiverInfo.get().getId();

        if (ownerId.equals(receiverId)) {
            throw new IllegalArgumentException("Cannot create a chat with yourself.");
        }

        //duplicate chat check
        Optional<ChatModelCreation> existingChat = chatRepository.findByOwnerIdAndReceiverId(ownerId, receiverId);
        if (existingChat.isEmpty()) {
            //check in reverse order:the receiver could have been the owner
            existingChat = chatRepository.findByOwnerIdAndReceiverId(receiverId, ownerId);
        }
        if (existingChat.isPresent()) {
            throw new RuntimeException("Chat already exists between these users.");
        }


        chatModelCreation.setOwnerId(ownerId);
        chatModelCreation.setReceiverId(receiverId);

        return chatRepository.save(chatModelCreation);
    }

    @Transactional
    public boolean deleteChatById(Long chatId, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null for delete operation.");
        }

        ChatModelCreation chat = chatRepository.findByChatId(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found with ID: " + chatId)); // Custom NotFoundException preferred

        if (!chat.getOwnerId().equals(userId)) {
            System.err.printf("Delete chat %d denied for user %d (owner is %d)%n", chatId, userId, chat.getOwnerId());
            throw new AccessDeniedException("You do not have permission to delete this chat.");
        }

        chatRepository.deleteByChatId(chatId);
        chatMessageRepo.deleteByChatId(chatId);
        return true;
    }

    public List<ChatModelCreation> getChatsForUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null.");
        }
        return chatRepository.findByOwnerIdOrReceiverId(userId, userId);
    }

    public boolean isUserInChat(String username, Long chatId) {
        Optional<UserModel> userOpt = userRepo.findByUsername(username);
        if (userOpt.isEmpty()) {
            return false;
        }
        Long userId = userOpt.get().getId();

        Optional<ChatModelCreation> chatOpt = chatRepository.findByChatId(chatId);
        if (chatOpt.isEmpty()) {
            return false;
        }
        ChatModelCreation chat = chatOpt.get();
        return chat.getOwnerId().equals(userId) || chat.getReceiverId().equals(userId);
    }
}