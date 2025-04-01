package com.chat.service;

import com.chat.model.ChatModelCreation;
import com.chat.model.UserModel;
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
    // Remove HttpSession

    @Autowired
    public ChatService(ChatRepository chatRepository, UserRepo userRepo) {
        this.chatRepository = chatRepository;
        this.userRepo = userRepo;
    }

    // Method updated to take ownerId
    public ChatModelCreation createChat(ChatModelCreation chatModelCreation, Long ownerId) {
        // Validate ownerId exists? Usually guaranteed if obtained from Authentication
        if (ownerId == null) { // Basic check
            throw new IllegalArgumentException("Owner ID cannot be null.");
        }

        // Find receiver by username provided in the request
        Optional<UserModel> receiverInfo = userRepo.findByUsername(chatModelCreation.getReceiverName());
        if (receiverInfo.isEmpty()) {
            throw new RuntimeException("Receiver User Not Found: " + chatModelCreation.getReceiverName()); // Use custom exception?
        }

        Long receiverId = receiverInfo.get().getId();

        // Prevent creating chat with oneself?
        if (ownerId.equals(receiverId)) {
            throw new IllegalArgumentException("Cannot create a chat with yourself.");
        }


        // TODO: Check if a chat between these two users already exists?


        chatModelCreation.setOwnerId(ownerId); // Set the authenticated user as owner
        chatModelCreation.setReceiverId(receiverId);
        // chatName might be generated or validated here

        return chatRepository.save(chatModelCreation);
    }

    // Method updated to take userId performing the delete action
    @Transactional
    public boolean deleteChatById(Long chatId, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null for delete operation.");
        }

        ChatModelCreation chat = chatRepository.findByChatId(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found with ID: " + chatId)); // Custom NotFoundException preferred

        // Authorization Check: Only owner or receiver(?) can delete the chat.
        // Decide your deletion logic. Here, only the owner can delete.
        if (!chat.getOwnerId().equals(userId)) {
            System.err.printf("Delete chat %d denied for user %d (owner is %d)%n", chatId, userId, chat.getOwnerId());
            throw new AccessDeniedException("You do not have permission to delete this chat.");
        }

        chatRepository.deleteByChatId(chatId);
        // TODO: Delete associated messages from MongoDB as well? (Requires ChatMessageRepo injection)
        // chatMessageRepo.deleteByChatId(chatId); // If you add such a method
        return true;
    }

    // Method updated to take userId
    public List<ChatModelCreation> getChatsForUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null.");
        }
        // Find chats where the user is either the owner or the receiver
        return chatRepository.findByOwnerIdOrReceiverId(userId, userId);
    }

    // --- New Method for Authorization Checks ---
    public boolean isUserInChat(String username, Long chatId) {
        Optional<UserModel> userOpt = userRepo.findByUsername(username);
        if (userOpt.isEmpty()) {
            return false; // User doesn't exist
        }
        Long userId = userOpt.get().getId();

        Optional<ChatModelCreation> chatOpt = chatRepository.findByChatId(chatId);
        if (chatOpt.isEmpty()) {
            return false; // Chat doesn't exist
        }

        ChatModelCreation chat = chatOpt.get();
        // Check if the user is the owner OR the receiver
        return chat.getOwnerId().equals(userId) || chat.getReceiverId().equals(userId);
    }
}