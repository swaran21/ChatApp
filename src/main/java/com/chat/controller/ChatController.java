package com.chat.controller;

// ... other imports ...
import com.chat.model.ChatMessage;
import com.chat.model.ChatMessageDTO;
import com.chat.model.ChatModelCreation; // Assuming this is your Chat room/conversation entity
import com.chat.model.UserModel;
import com.chat.repo.ChatMessageRepo;
import com.chat.repo.ChatRepository;
import com.chat.service.ChatService;
import com.chat.service.UserService; // Needed to get UserModel from username
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.access.AccessDeniedException; // Use for authorization failures
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
// @CrossOrigin removed - handled by SecurityConfig
public class ChatController {
    private final ChatService chatService;
    private final ChatMessageRepo chatMessageRepo;
    private final UserService userService; // Inject UserService

    @Autowired
    public ChatController(ChatService chatService, ChatRepository chatRepository, ChatMessageRepo chatMessageRepo, UserService userService) {
        this.chatService = chatService;
        this.chatMessageRepo = chatMessageRepo;
        this.userService = userService;
    }

    // --- Helper to get authenticated user's ID ---
    private Long getAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User not authenticated"); // Or handle differently
        }
        String username = authentication.getName();
        // Fetch the UserModel to get the ID
        UserModel user = userService.getUserModelByUsername(username)
                .orElseThrow(() -> new SecurityException("Authenticated user not found in database"));
        return user.getId();
    }

    // --- REST Endpoints modified ---
    @PostMapping("/create")
    public ResponseEntity<?> createChat(@RequestBody ChatModelCreation chatModelCreation, Authentication authentication) {
        try {
            Long ownerId = getAuthenticatedUserId(authentication);
            // Pass ownerId to the service method (assuming ChatService is updated)
            ChatModelCreation createdChat = chatService.createChat(chatModelCreation, ownerId);
            return ResponseEntity.ok(Map.of("message", "Chat created successfully!", "chat", createdChat));
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) { // Catch other service exceptions
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> getAllChats(Authentication authentication) {
        try {
            Long userId = getAuthenticatedUserId(authentication);
            // Pass userId to the service method (assuming ChatService is updated)
            List<ChatModelCreation> chats = chatService.getChatsForUser(userId);
            return ResponseEntity.ok(chats);
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteChat(@RequestParam("chatId") Long chatId, Authentication authentication) {
        try {
            Long userId = getAuthenticatedUserId(authentication);
            // Pass userId for authorization check in the service
            boolean deleted = chatService.deleteChatById(chatId, userId);
            if (deleted) {
                return ResponseEntity.ok(Map.of("message", "Chat deleted successfully!"));
            } else {
                // This case might not be reachable if service throws exception on not found/auth failure
                return ResponseEntity.status(404).body(Map.of("message", "Chat not found."));
            }
        } catch (SecurityException e) { // Catch auth error from helper
            return ResponseEntity.status(401).body(Map.of("message", e.getMessage()));
        } catch (AccessDeniedException e) { // Catch specific authz error from service
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) { // Catch other errors like chat not found from service
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // --- WebSocket Message Handler modified ---
    @MessageMapping("/chat/{chatId}/send")
    @SendTo("/topic/chat/{chatId}")
    public ChatMessageDTO sendMessage(
            @DestinationVariable Long chatId,
            @Payload ChatMessageDTO clientMessageDto,
            Authentication authentication // Inject Authentication Principal
    ) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            System.err.println("sendMessage denied: Unauthenticated access attempt.");
            // Ideally, Spring Security interceptors should prevent this call entirely.
            // Throwing an exception is appropriate here.
            throw new AccessDeniedException("Authentication required to send messages.");
        }

        String username = authentication.getName(); // Get username SECURELY

        // *** AUTHORIZATION CHECK (Crucial!) ***
        // Does 'username' have permission to send to 'chatId'?
        boolean userAllowedInChat = chatService.isUserInChat(username, chatId); // Need to implement this in ChatService
        if (!userAllowedInChat) {
            System.err.printf("sendMessage denied: User '%s' not authorized for chat %d.%n", username, chatId);
            throw new AccessDeniedException("User not authorized for this chat.");
        }
        // *** END AUTHORIZATION CHECK ***


        ChatMessage messageToSave;
        // ... (Logic for handling TEXT/VOICE based on clientMessageDto.getType())
        if ("VOICE".equals(clientMessageDto.getType())) {
            if (clientMessageDto.getContent() == null || clientMessageDto.getContent().isEmpty()) {
                System.err.println("Error: Received VOICE message without content.");
                throw new IllegalArgumentException("Voice message content cannot be empty.");
            }
            try {
                byte[] audioBytes = Base64.getDecoder().decode(clientMessageDto.getContent());
                messageToSave = new ChatMessage(chatId, username, audioBytes, clientMessageDto.getAudioMimeType(), "VOICE");
            } catch (IllegalArgumentException e) {
                System.err.println("Error decoding Base64 audio data: " + e.getMessage());
                throw new IllegalArgumentException("Invalid Base64 audio data.");
            }

        } else if ("TEXT".equals(clientMessageDto.getType())) {
            messageToSave = new ChatMessage(chatId, username, clientMessageDto.getContent(), "TEXT");
        } else {
            System.err.println("Error: Received unknown message type: " + clientMessageDto.getType());
            throw new IllegalArgumentException("Unsupported message type: " + clientMessageDto.getType());
        }

        ChatMessage savedMessage = chatMessageRepo.save(messageToSave);
        return ChatMessageDTO.fromEntity(savedMessage); // Convert to DTO for broadcast
    }

    // --- REST Endpoint to fetch chat history modified ---
    @GetMapping("/{chatId}")
    public List<ChatMessageDTO> getChatMessages(@PathVariable Long chatId, Authentication authentication) {
        String username = authentication.getName(); // Get authenticated username

        // *** AUTHORIZATION CHECK ***
        boolean userAllowedInChat = chatService.isUserInChat(username, chatId); // Reuse the check
        if (!userAllowedInChat) {
            System.err.printf("getChatMessages denied: User '%s' not authorized for chat %d.%n", username, chatId);
            throw new AccessDeniedException("User not authorized to view messages for this chat.");
        }
        // *** END AUTHORIZATION CHECK ***

        List<ChatMessage> messages = chatMessageRepo.findByChatIdOrderByTimestampAsc(chatId);
        return messages.stream()
                .map(ChatMessageDTO::fromEntity)
                .collect(Collectors.toList());
    }
}