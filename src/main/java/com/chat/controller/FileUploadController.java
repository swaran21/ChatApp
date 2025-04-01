package com.chat.controller;

import com.chat.service.CloudinaryService; // Import CloudinaryService
// import com.chat.service.ChatService; // Inject if needed for authz
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// Remove local file imports if no longer needed
// import java.io.File;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.nio.file.Paths;
// import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.util.List;
import java.util.Map;
// import java.util.UUID; // No longer needed for filename here

@RestController
@RequestMapping("/api/files") // Using dedicated path now
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);

    @Autowired
    private CloudinaryService cloudinaryService; // Inject CloudinaryService

    // @Autowired
    // private ChatService chatService; // Inject ChatService if needed for authorization

    // Allowed file types (consider making this configurable)
    // Allow common document types, images, video, audio
    private final List<String> allowedFileTypes = List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/webm", "video/quicktime",
            "audio/mpeg", "audio/ogg", "audio/wav",
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
            "text/plain"
    );

    // Simple filename sanitization (you might want a more robust library)
    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null) return "upload";
        // Basic sanitization: replace non-alphanumeric/dot/hyphen/underscore with underscore
        return originalFileName.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
    }


    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            // @RequestParam(value = "chatId", required = false) Long chatId, // Keep if needed for authz
            Authentication authentication
    ) {
        // 1. Authentication Check
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Authentication required."));
        }
        String username = authentication.getName();

        // 2. Basic File Validation
        if (file == null || file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "No file provided."));
        }
        String contentType = file.getContentType();
        if (contentType == null || !allowedFileTypes.contains(contentType.toLowerCase())) {
            log.warn("Upload denied for user {}: Invalid file type '{}'", username, contentType);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Invalid file type: " + contentType));
        }
        // Add size check? e.g., if (file.getSize() > MAX_FILE_SIZE) ...

        // 3. Authorization Check (Optional - Can user upload to this context/chat?)
        // if (chatId != null) {
        //     boolean allowed = chatService.isUserInChat(username, chatId);
        //     if (!allowed) {
        //         log.warn("Upload denied for user {} to chat {}: Not authorized", username, chatId);
        //         return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Not authorized to upload to this chat."));
        //     }
        // }

        String originalFileName = sanitizeFileName(file.getOriginalFilename());

        try {
            // --- Call Cloudinary Service ---
            Map<String, Object> uploadResult = cloudinaryService.uploadFile(file, originalFileName);
            String fileUrl = (String) uploadResult.get("secure_url"); // Get the Cloudinary URL

            if (fileUrl == null) {
                log.error("Cloudinary upload succeeded but no secure_url was returned for file: {}", originalFileName);
                throw new RuntimeException("File upload failed: Could not retrieve file URL.");
            }


            // --- Prepare Response for Frontend ---
            // Frontend will use this info to send the WebSocket message
            Map<String, Object> response = Map.of(
                    "message", "File uploaded successfully",
                    "fileName", originalFileName, // Send original (sanitized) name back
                    "fileUrl", fileUrl,           // The Cloudinary URL
                    "fileType", contentType,       // The original MIME type
                    "fileSize", uploadResult.getOrDefault("bytes", 0L), // Size from Cloudinary result
                    "uploadedBy", username
                    // "publicId", uploadResult.get("public_id") // Optionally send public_id if frontend needs it
            );

            return ResponseEntity.ok(response);

        } catch (IOException e) { // Catch IO errors reading the file
            log.error("File reading failed for user {} during upload preparation: {}", username, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "File upload failed: Could not read file data."));
        } catch (RuntimeException e) { // Catch errors from CloudinaryService (upload failure)
            log.error("Cloudinary upload failed for user {}: {}", username, e.getMessage(), e); // Log stack trace too
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "File upload failed: " + e.getMessage()));
        }
    }
}