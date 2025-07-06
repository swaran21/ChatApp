package com.chat.service;

import com.chat.model.ChatMessage;
import com.chat.model.ChatMessageDTO;
import com.chat.repo.ChatMessageRepo;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    // Using gemini-1.5-flash as it's fast and effective for chat.
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    private final RestTemplate restTemplate;
    private final ChatMessageRepo chatMessageRepo;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Autowired
    public GeminiService(RestTemplate restTemplate, ChatMessageRepo chatMessageRepo, SimpMessagingTemplate messagingTemplate) {
        this.restTemplate = restTemplate;
        this.chatMessageRepo = chatMessageRepo;
        this.messagingTemplate = messagingTemplate;
    }

    @Async // Run this method in a separate thread to avoid blocking
    public void generateResponseAndBroadcast(Long chatId, String userMessage) {
        log.info("Generating Gemini response for chat ID: {} with API Key ending in ...{}", chatId, geminiApiKey.substring(Math.max(0, geminiApiKey.length() - 4)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-goog-api-key", geminiApiKey);

        // Build the request body to match the API specification
        GeminiRequest.Part part = new GeminiRequest.Part(userMessage);
        GeminiRequest.Content content = new GeminiRequest.Content(Collections.singletonList(part));
        GeminiRequest requestPayload = new GeminiRequest(Collections.singletonList(content));

        HttpEntity<GeminiRequest> entity = new HttpEntity<>(requestPayload, headers);

        try {
            // Simulate a slight typing delay for better user experience
            Thread.sleep(1500);

            GeminiResponse response = restTemplate.postForObject(GEMINI_API_URL, entity, GeminiResponse.class);

            String aiTextResponse = extractTextFromResponse(response);
            if (aiTextResponse.isBlank()) {
                log.warn("Gemini returned a blank response for chat ID: {}", chatId);
                return;
            }

            // Create and save the AI's message
            ChatMessage aiMessage = new ChatMessage(chatId, "GeminiAI", aiTextResponse, "TEXT");
            ChatMessage savedAiMessage = chatMessageRepo.save(aiMessage);

            // Convert to DTO and broadcast to the user
            ChatMessageDTO broadcastDTO = ChatMessageDTO.fromEntity(savedAiMessage);
            messagingTemplate.convertAndSend("/topic/chat/" + chatId, broadcastDTO);
            log.info("Successfully generated and broadcast AI response to chat ID: {}", chatId);

        } catch (Exception e) {
            log.error("Error calling Gemini API for chat ID {}: {}", chatId, e.getMessage(), e);
            // Send an error message back to the user's chat
            ChatMessage errorMessage = new ChatMessage(chatId, "GeminiAI", "Sorry, I couldn't connect to my brain. Please try again.", "TEXT");
            ChatMessageDTO errorDTO = ChatMessageDTO.fromEntity(errorMessage);
            messagingTemplate.convertAndSend("/topic/chat/" + chatId, errorDTO);
        }
    }

    private String extractTextFromResponse(GeminiResponse response) {
        if (response != null && response.getCandidates() != null && !response.getCandidates().isEmpty()) {
            GeminiResponse.Candidate firstCandidate = response.getCandidates().get(0);
            if (firstCandidate != null && firstCandidate.getContent() != null && firstCandidate.getContent().getParts() != null && !firstCandidate.getContent().getParts().isEmpty()) {
                return firstCandidate.getContent().getParts().get(0).getText();
            }
        }
        return "";
    }

    // --- DTO classes to map the JSON request/response structure ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class GeminiRequest {
        private List<Content> contents;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Content {
            private List<Part> parts;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Part {
            private String text;
        }
    }

    @Data
    @NoArgsConstructor
    private static class GeminiResponse {
        private List<Candidate> candidates;

        @Data
        @NoArgsConstructor
        public static class Candidate {
            private Content content;
        }

        @Data
        @NoArgsConstructor
        public static class Content {
            private List<Part> parts;
            private String role;
        }

        @Data
        @NoArgsConstructor
        public static class Part {
            private String text;
        }
    }
}