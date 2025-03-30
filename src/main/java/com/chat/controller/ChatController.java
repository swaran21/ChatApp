package com.chat.controller;

import com.chat.model.ChatMessage;
import com.chat.model.ChatModelCreation;
import com.chat.model.UserModel;
import com.chat.repo.ChatRepository;
import com.chat.service.ChatService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true") // ✅ Apply at class level
public class ChatController {
    private final ChatService chatService;
    @Autowired
    public ChatController(ChatService chatService, ChatRepository chatRepository) {
        this.chatService = chatService;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createChat(@RequestBody ChatModelCreation chatModelCreation, HttpSession session) {
        UserModel user = (UserModel) session.getAttribute("owner");
        Long ownerId = user.getId();
        if (ownerId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "You are not logged in."));
        }
        chatModelCreation.setOwnerId(ownerId);
        ChatModelCreation createdChat = chatService.createChat(chatModelCreation);
        return ResponseEntity.ok(Map.of("message", "Chat created successfully!", "chat", createdChat));
    }


    @GetMapping("/list")
    public ResponseEntity<?> getAllChats(HttpSession session) {
        UserModel user = (UserModel) session.getAttribute("owner");
        Long ownerId = user.getId();
        if (ownerId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "You are not logged in."));
        }
        List<ChatModelCreation> chats = chatService.getChatsByOwnerId();
        return ResponseEntity.ok(chats);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteChat(@RequestParam("chatId") Long chatId) {
        if (chatId == null) {
            return ResponseEntity.status(401).body("You are not logged in.");
        }
        boolean deleted = chatService.deleteChatById(chatId);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Chat deleted successfully!"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("message", "Chat not found or could not be deleted."));
        }
    }

    @MessageMapping("/chat/{chatId}/send")
    @SendTo("/topic/chat/{chatId}")
    public ChatMessage sendMessage(ChatMessage message) {
        return message;
    }

}
