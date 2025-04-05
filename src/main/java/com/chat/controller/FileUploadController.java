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


@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);

    @Autowired
    private CloudinaryService cloudinaryService;

    private final List<String> allowedFileTypes = List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "audio/mpeg", "audio/ogg", "audio/wav",
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
    );

    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null) return "upload";
        //replace non-alphanumeric/dot/hyphen/underscore with underscore
        return originalFileName.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
    }


    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Authentication required."));
        }
        String username = authentication.getName();

        if (file == null || file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "No file provided."));
        }
        String contentType = file.getContentType();
        if (contentType == null || !allowedFileTypes.contains(contentType.toLowerCase())) {
            log.warn("Upload denied for user {}: Invalid file type '{}'", username, contentType);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "Invalid file type: " + contentType));
        }
        String originalFileName = sanitizeFileName(file.getOriginalFilename());

        try {
            Map<String, Object> uploadResult = cloudinaryService.uploadFile(file, originalFileName);
            String fileUrl = (String) uploadResult.get("secure_url");

            if (fileUrl == null) {
                log.error("Cloudinary upload succeeded but no secure_url was returned for file: {}", originalFileName);
                throw new RuntimeException("File upload failed: Could not retrieve file URL.");
            }

            Map<String, Object> response = Map.of(
                    "message", "File uploaded successfully",
                    "fileName", originalFileName,
                    "fileUrl", fileUrl,
                    "fileType", contentType,
                    "fileSize", uploadResult.getOrDefault("bytes", 0L),
                    "uploadedBy", username
            );

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("File reading failed for user {} during upload preparation: {}", username, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "File upload failed: Could not read file data."));
        } catch (RuntimeException e) {
            log.error("Cloudinary upload failed for user {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "File upload failed: " + e.getMessage()));
        }
    }
}