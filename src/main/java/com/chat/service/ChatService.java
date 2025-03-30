package com.chat.service;

import com.chat.model.ChatModelCreation;
import com.chat.model.UserModel;
import com.chat.repo.ChatRepository;
import com.chat.repo.UserRepo;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.apache.catalina.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class ChatService {

    private final ChatRepository chatRepository;
    private final UserRepo userRepo;
    private final HttpSession session;

    @Autowired
    public ChatService(ChatRepository chatRepository, UserRepo userRepo, HttpSession session) {
        this.chatRepository = chatRepository;
        this.userRepo = userRepo;
        this.session = session;
    }

    public ChatModelCreation createChat(ChatModelCreation chatModelCreation) {
        UserModel owner = (UserModel) session.getAttribute("owner");
        Long ownerId = owner.getId();
        if (ownerId == null) {
            throw new RuntimeException("You are not logged in");
        }
        Optional<UserModel> receiverInfo = userRepo.findByUsername(chatModelCreation.getReceiverName());
        if (receiverInfo.isEmpty()) {
            throw new RuntimeException("User Not Found");
        }

        Long receiverId = receiverInfo.get().getId();
        chatModelCreation.setReceiverId(receiverId);

        chatModelCreation.setOwnerId(ownerId);
        return chatRepository.save(chatModelCreation);
    }

    @Transactional
    public boolean deleteChatById(Long chatId) {
        UserModel owner = (UserModel) session.getAttribute("owner");
        Long ownerId = owner.getId();
        if (ownerId == null) {
            throw new RuntimeException("You are not logged in");
        }

        Optional<ChatModelCreation> chatOpt = chatRepository.findByChatId(chatId);
        if (chatOpt.isPresent()) {
            ChatModelCreation chat = chatOpt.get();
            // Check if the chat belongs to the owner
            if (!chat.getOwnerId().equals(ownerId)) {
                throw new RuntimeException("You do not have permission to delete this chat");
            }
            chatRepository.deleteByChatId(chatId);
            return true;
        }
        return false;
    }


    public List<ChatModelCreation> getChatsByOwnerId() {
        UserModel owner = (UserModel) session.getAttribute("owner");
        Long ownerId = owner.getId();
        if (ownerId == null) {
            throw new RuntimeException("You are not logged in");
        }
        return chatRepository.findByOwnerIdOrReceiverId(ownerId,ownerId);
    }
}
