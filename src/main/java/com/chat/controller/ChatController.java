package com.chat.controller;

import com.chat.model.ChatModelCreation; // If needed for other methods
import com.chat.model.ChatMessage;
import com.chat.model.ChatMessageDTO;
import com.chat.repo.ChatMessageRepo;
import com.chat.service.ChatService; // Inject ChatService
import com.chat.service.UserService; // Inject if needed for user lookup/validation
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate; // Crucial for broadcasting
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*; // Keep REST annotations

import java.util.Base64; // Import Base64
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController // Keep if it also has REST endpoints
// Or @Controller if it ONLY has WebSocket mappings now
@RequestMapping("/api/chat") // Base path for REST endpoints (if any)
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final UserService userService; // Inject if needed
    private final ChatMessageRepo chatMessageRepository; // Inject Mongo Repo
    private final SimpMessagingTemplate messagingTemplate; // Inject template

    @Autowired
    public ChatController(ChatService chatService, UserService userService,
                          ChatMessageRepo chatMessageRepository,
                          SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.userService = userService;
        this.chatMessageRepository = chatMessageRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // --- Existing REST Endpoints (Keep them as they are) ---

    @PostMapping("/create")
    public ResponseEntity<?> createChat(@RequestBody ChatModelCreation chatModelCreation, Authentication authentication) {
        // ... (your existing create chat logic using authentication.getName() to find ownerId)
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "Authentication required."));
        }
        String username = authentication.getName();
        Long ownerId = userService.getUserModelByUsername(username)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"))
                .getId();

        try {
            ChatModelCreation createdChat = chatService.createChat(chatModelCreation, ownerId);
            return ResponseEntity.ok(createdChat);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage())); // E.g., Receiver not found
        }
        // ... other catches
    }

    @GetMapping("/list")
    public ResponseEntity<?> getChats(Authentication authentication) {
        // ... (your existing get chats logic using authentication.getName() to find userId)
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "Authentication required."));
        }
        String username = authentication.getName();
        Long userId = userService.getUserModelByUsername(username)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"))
                .getId();
        List<ChatModelCreation> chats = chatService.getChatsForUser(userId);
        return ResponseEntity.ok(chats);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteChat(@RequestParam Long chatId, Authentication authentication) {
        // ... (your existing delete chat logic using authentication.getName() to find userId)
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "Authentication required."));
        }
        String username = authentication.getName();
        Long userId = userService.getUserModelByUsername(username)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"))
                .getId();

        try {
            boolean deleted = chatService.deleteChatById(chatId, userId);
            if (deleted) {
                // Consider deleting messages from MongoDB here too
                // chatMessageRepository.deleteByChatId(chatId); // If you implement this method
                return ResponseEntity.ok(Map.of("message", "Chat deleted successfully!"));
            } else {
                // This path might not be reached if service throws exceptions
                return ResponseEntity.status(404).body(Map.of("message", "Chat not found or deletion failed."));
            }
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage())); // E.g., Chat not found
        }
    }

    @GetMapping("/{chatId}")
    public ResponseEntity<?> getMessages(@PathVariable Long chatId, Authentication authentication) {
        // --- Authorization Check: Ensure user is part of this chat ---
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "Authentication required."));
        }
        String username = authentication.getName();
        if (!chatService.isUserInChat(username, chatId)) {
            log.warn("User '{}' attempted to access messages for chat {} but is not a member.", username, chatId);
            return ResponseEntity.status(403).body(Map.of("message", "You are not authorized to view messages for this chat."));
        }
        // --- End Authorization Check ---

        List<ChatMessage> messages = chatMessageRepository.findByChatIdOrderByTimestampAsc(chatId);
        // Convert List<ChatMessage> to List<ChatMessageDTO>
        List<ChatMessageDTO> messageDTOs = messages.stream()
                .map(ChatMessageDTO::fromEntity)
                .toList(); // Java 16+ shorthand
        return ResponseEntity.ok(messageDTOs);
    }


    // --- WebSocket Message Handling Method (Consolidated) ---
    @MessageMapping("/chat/{chatId}/send") // The single mapping for sending messages
    // Method returns void because we manually send with SimpMessagingTemplate
    public void handleAndBroadcastMessage(@DestinationVariable Long chatId,
                                          @Payload ChatMessageDTO messageDTO, // Receives the DTO from client
                                          Authentication authentication) { // Verify sender

        if (messageDTO == null || messageDTO.getType() == null || messageDTO.getSender() == null) {
            log.warn("Received invalid message payload for chat {}: {}", chatId, messageDTO);
            // Optionally send error back to sender?
            return;
        }

        // --- Security Check: Verify sender matches authenticated user ---
        String authenticatedUsername = authentication != null ? authentication.getName() : null;
        if (authenticatedUsername == null || !authenticatedUsername.equals(messageDTO.getSender())) {
            log.warn("Sender mismatch or unauthenticated user! Auth user: '{}', Payload sender: '{}' for chat {}",
                    authenticatedUsername, messageDTO.getSender(), chatId);
            // Overwrite sender from authenticated principal if available
            if (authenticatedUsername != null) {
                log.debug("Overwriting sender in DTO for chat {} to authenticated user '{}'", chatId, authenticatedUsername);
                messageDTO.setSender(authenticatedUsername);
            } else {
                log.error("Cannot process message for chat {}: User not authenticated.", chatId);
                // Optionally send error message back to sender's session?
                return; // Stop processing if user isn't authenticated
            }
        }
        // --- End Security Check ---

        // --- Authorization Check: Ensure authenticated user is allowed in this chat ---
        if (!chatService.isUserInChat(authenticatedUsername, chatId)) {
            log.warn("User '{}' attempted to send message to chat {} but is not a member.", authenticatedUsername, chatId);
            // Optionally send error message back to sender's session?
            return; // Stop processing
        }
        // --- End Authorization Check ---


        ChatMessage messageEntity;
        log.debug("Processing message DTO for chat {}: Type='{}', Sender='{}'", chatId, messageDTO.getType(), messageDTO.getSender());

        // Create the correct ChatMessage entity based on type from DTO
        try {
            switch (messageDTO.getType()) {
                case "TEXT":
                    messageEntity = new ChatMessage(chatId, messageDTO.getSender(), messageDTO.getContent(), "TEXT");
                    break;
                case "VOICE":
                    byte[] audioData = null;
                    if (messageDTO.getContent() != null) {
                        audioData = Base64.getDecoder().decode(messageDTO.getContent());
                    } else {
                        log.warn("Received VOICE message for chat {} with null content.", chatId);
                        // Decide how to handle: error message or empty audio?
                        messageEntity = new ChatMessage(chatId, messageDTO.getSender(), "[Empty voice message]", "TEXT"); // Example: Save as text error
                        break;
                    }
                    messageEntity = new ChatMessage(chatId, messageDTO.getSender(), audioData, messageDTO.getAudioMimeType(), "VOICE");
                    // messageEntity.setContent(null); // Optionally clear base64 content if only storing bytes
                    break;
                case "FILE_URL":
                    if (messageDTO.getContent() == null || messageDTO.getFileName() == null || messageDTO.getFileType() == null) {
                        log.error("Received FILE_URL message for chat {} with missing data: URL='{}', Name='{}', Type='{}'", chatId, messageDTO.getContent(), messageDTO.getFileName(), messageDTO.getFileType());
                        messageEntity = new ChatMessage(chatId, messageDTO.getSender(), "[Failed to process file message - missing data]", "TEXT"); // Example: Save as text error
                        break;
                    }
                    messageEntity = new ChatMessage(chatId, messageDTO.getSender(), messageDTO.getContent(), messageDTO.getFileName(), messageDTO.getFileType(), "FILE_URL");
                    break;
                default:
                    log.warn("Received message with unknown type '{}' for chat {}", messageDTO.getType(), chatId);
                    messageEntity = new ChatMessage(chatId, messageDTO.getSender(), "[Unsupported message type: " + messageDTO.getType() + "]", "TEXT");
                    break;
            }
        } catch (IllegalArgumentException e) {
            log.error("Error processing message content for chat {} (e.g., invalid Base64): {}", chatId, e.getMessage());
            messageEntity = new ChatMessage(chatId, messageDTO.getSender(), "[Error processing message content]", "TEXT");
            // You might want to notify the sender here as well
        }

        // Save the message entity to MongoDB
        ChatMessage savedMessage = chatMessageRepository.save(messageEntity);
        log.info("Saved message ID {} (Type: {}) for chat {}", savedMessage.getId(), savedMessage.getType(), chatId);

        // Convert the *saved* entity back to DTO (to include ID, timestamp, and correct fields)
        ChatMessageDTO broadcastDTO = ChatMessageDTO.fromEntity(savedMessage);

        // Broadcast the DTO to all subscribers of the chat topic
        String destination = "/topic/chat/" + chatId;
        messagingTemplate.convertAndSend(destination, broadcastDTO);
        log.debug("Broadcasted message DTO to {}: {}", destination, broadcastDTO);

        // Note: No return value needed here as we used messagingTemplate.convertAndSend
        // If you used @SendTo annotation on the method, you would return broadcastDTO here.
    }

}