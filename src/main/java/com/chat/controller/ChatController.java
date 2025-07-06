package com.chat.controller;

import com.chat.model.ChatModelCreation;
import com.chat.model.ChatMessage;
import com.chat.model.ChatMessageDTO;
import com.chat.repo.ChatMessageRepo;
import com.chat.service.ChatService;
import com.chat.service.GeminiService;
import com.chat.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final UserService userService;
    private final ChatMessageRepo chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final GeminiService geminiService;

    @Autowired
    public ChatController(ChatService chatService, UserService userService,
                          ChatMessageRepo chatMessageRepository,
                          SimpMessagingTemplate messagingTemplate, GeminiService geminiService) {
        this.chatService = chatService;
        this.userService = userService;
        this.chatMessageRepository = chatMessageRepository;
        this.messagingTemplate = messagingTemplate;
        this.geminiService = geminiService;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createChat(@RequestBody ChatModelCreation chatModelCreation, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "Authentication required."));
        }
        String username = authentication.getName();
        try {
            Long ownerId = userService.getUserModelByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Authenticated user profile not found"))
                    .getId();
            ChatModelCreation createdChat = chatService.createChat(chatModelCreation, ownerId);
            return ResponseEntity.ok(createdChat);
        } catch (RuntimeException e) {
            log.warn("Failed to create chat for user {}: {}", username, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error creating chat for user {}", username, e);
            return ResponseEntity.status(500).body(Map.of("message", "An internal error occurred while creating the chat."));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<?> getChats(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "Authentication required."));
        }
        String username = authentication.getName();
        try {
            Long userId = userService.getUserModelByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Authenticated user profile not found"))
                    .getId();
            List<ChatModelCreation> chats = chatService.getChatsForUser(userId);
            return ResponseEntity.ok(chats);
        } catch (RuntimeException e) {
            log.warn("Failed to list chats for user {}: {}", username, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error listing chats for user {}", username, e);
            return ResponseEntity.status(500).body(Map.of("message", "An internal error occurred while listing chats."));
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteChat(@RequestParam Long chatId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "Authentication required."));
        }
        String username = authentication.getName();
        try {
            Long userId = userService.getUserModelByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Authenticated user profile not found"))
                    .getId();
            boolean deleted = chatService.deleteChatById(chatId, userId);
            if (deleted) {

                return ResponseEntity.ok(Map.of("message", "Chat deleted successfully!"));
            } else {
                return ResponseEntity.status(404).body(Map.of("message", "Chat not found or already deleted."));
            }
        } catch (AccessDeniedException e) {
            log.warn("Auth Denied: User '{}' attempted to delete chat {} without permission.", username, chatId);
            return ResponseEntity.status(403).body(Map.of("message", "You do not have permission to delete this chat."));
        } catch (RuntimeException e) {
            log.warn("Failed to delete chat {} for user {}: {}", chatId, username, e.getMessage());
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error deleting chat {} for user {}", chatId, username, e);
            return ResponseEntity.status(500).body(Map.of("message", "An internal error occurred while deleting the chat."));
        }
    }

    @GetMapping("/{chatId}")
    public ResponseEntity<?> getMessages(@PathVariable Long chatId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "Authentication required."));
        }
        String username = authentication.getName();
        try {
            if (!chatService.isUserInChat(username, chatId)) {
                log.warn("Auth Denied: User '{}' requesting messages for chat {} they are not in.", username, chatId);
                return ResponseEntity.status(403).body(Map.of("message", "Not authorized for this chat."));
            }

            List<ChatMessage> messages = chatMessageRepository.findByChatIdOrderByTimestampAsc(chatId);
            List<ChatMessageDTO> messageDTOs = messages.stream()
                    .map(ChatMessageDTO::fromEntity) // Use the static factory method
                    .collect(Collectors.toList());
            return ResponseEntity.ok(messageDTOs);
        } catch (RuntimeException e) {
            log.warn("Failed to get messages for chat {} for user {}: {}", chatId, username, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error getting messages for chat {} for user {}", chatId, username, e);
            return ResponseEntity.status(500).body(Map.of("message", "An internal error occurred while fetching messages."));
        }
    }

    @MessageMapping("/chat/{chatId}/send")
    public void handleAndBroadcastMessage(@DestinationVariable Long chatId,
                                          @Payload ChatMessageDTO messageDTO,
                                           Authentication authentication) {

        if (messageDTO == null || messageDTO.getType() == null || messageDTO.getSender() == null) {
            log.warn("WS Request Invalid: Received null/incomplete message payload for chat {}: {}", chatId, messageDTO);
            return;
        }

        String authenticatedUsername = authentication != null ? authentication.getName() : null;
        if (authenticatedUsername == null) {
            log.error("WS Request Denied: Unauthenticated user attempted to send message to chat {}", chatId);
            return;
        }
        if (!authenticatedUsername.equals(messageDTO.getSender())) {
            log.warn("WS Sender Mismatch: Auth user '{}' differs from payload sender '{}' for chat {}. Overwriting sender.",
                    authenticatedUsername, messageDTO.getSender(), chatId);
            messageDTO.setSender(authenticatedUsername);
        }
        if (!chatService.isUserInChat(authenticatedUsername, chatId)) {
            log.warn("WS Auth Denied: User '{}' attempted to send message to chat {} but is not a member.", authenticatedUsername, chatId);
            return;
        }

        ChatMessage messageEntity = null;
        log.debug("WS Processing: Chat={}, Type='{}', Sender='{}'", chatId, messageDTO.getType(), messageDTO.getSender());

        try {
            switch (messageDTO.getType()) {
                case "TEXT":
                    if (messageDTO.getContent() == null || messageDTO.getContent().trim().isEmpty()) {
                        log.warn("WS Skipping empty TEXT message for chat {}", chatId); return;
                    }
                    messageEntity = new ChatMessage(chatId, messageDTO.getSender(), messageDTO.getContent(), "TEXT");
                    break;
                case "FILE_URL":
                    if (messageDTO.getContent() == null || messageDTO.getFileName() == null || messageDTO.getFileType() == null) {
                        log.error("WS Invalid FILE_URL: Received FILE_URL message for chat {} with missing data: URL='{}', Name='{}', Type='{}'", chatId, messageDTO.getContent(), messageDTO.getFileName(), messageDTO.getFileType());
                        return; // Don't save invalid file messages
                    }
                    try { new java.net.URL(messageDTO.getContent()).toURI(); }
                    catch (Exception e) { log.error("WS Invalid FILE_URL: Content is not a valid URL: {}", messageDTO.getContent()); return; }

                    messageEntity = new ChatMessage(chatId, messageDTO.getSender(), messageDTO.getContent(), messageDTO.getFileName(), messageDTO.getFileType(), "FILE_URL");
                    break;
                default:
                    log.warn("WS Unknown Type: Received message with unknown type '{}' for chat {}", messageDTO.getType(), chatId);
                    return;
            }
        } catch (IllegalArgumentException e) {
            log.error("WS Processing Error: Failed to process content for message type {} in chat {} (e.g., invalid Base64): {}", messageDTO.getType(), chatId, e.getMessage());
            return;
        } catch (Exception e) {
            log.error("WS Processing Error: Unexpected error processing message type {} in chat {}: {}", messageDTO.getType(), chatId, e.getMessage(), e);
            return;
        }

        // Save and Broadcast if entity was successfully created
        ChatMessage savedMessage = chatMessageRepository.save(messageEntity);
        log.info("DB Saved: Message ID {} (Type: {}) for chat {}", savedMessage.getId(), savedMessage.getType(), chatId);

        // Convert the *saved* entity back to DTO (uses the fixed fromEntity method)
        ChatMessageDTO broadcastDTO = ChatMessageDTO.fromEntity(savedMessage);

        if (broadcastDTO == null) {
            log.error("WS Broadcast Error: Failed to convert saved entity (ID: {}) back to DTO for chat {}", savedMessage.getId(), chatId);
            return;
        }

        String destination = "/topic/chat/" + chatId;
        messagingTemplate.convertAndSend(destination, broadcastDTO);
        log.debug("WS Broadcast: Sent DTO to {} for chat {}", destination, chatId);

        if (chatService.isAiChat(chatId) && "TEXT".equals(savedMessage.getType())) {
            geminiService.generateResponseAndBroadcast(chatId, savedMessage.getContent());
        }

    }

}