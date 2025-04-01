// src/main/java/com/chat/service/CloudinaryService.java
package com.chat.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID; // For generating public IDs if needed

@Service
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    @Autowired
    private Cloudinary cloudinary;

    public Map<String, Object> uploadFile(MultipartFile file, String originalFilename) throws IOException {
        log.info("Attempting to upload file '{}' to Cloudinary.", originalFilename);

        // Generate a unique public ID (optional, Cloudinary generates one if not provided)
        // String publicId = "chat_files/" + UUID.randomUUID().toString();

        try {
            // Upload options:
            // resource_type: "auto" -> Let Cloudinary detect image, video, raw
            // folder: "chat_uploads" -> Organize uploads in Cloudinary (optional)
            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "resource_type", "auto",
                    "original_filename", originalFilename // Store original filename metadata
                    // "public_id", publicId // Use generated public ID if desired
                    // "folder", "chat_uploads" // Optional folder
            ));

            // Extract useful information from the result
            String secureUrl = (String) uploadResult.get("secure_url"); // HTTPS URL (recommended)
            String publicIdResult = (String) uploadResult.get("public_id");
            String resourceType = (String) uploadResult.get("resource_type");
            Number bytes = (Number) uploadResult.get("bytes");

            log.info("File '{}' uploaded successfully to Cloudinary. URL: {}, Public ID: {}, Type: {}, Size: {} bytes",
                    originalFilename, secureUrl, publicIdResult, resourceType, bytes);

            // Return a map containing essential details for the frontend/backend
            return Map.of(
                    "secure_url", secureUrl,
                    "public_id", publicIdResult,
                    "resource_type", resourceType,
                    "bytes", bytes != null ? bytes.longValue() : 0L
            );

        } catch (IOException e) {
            log.error("Failed to read file bytes for Cloudinary upload: {}", originalFilename, e);
            throw e; // Re-throw IO exception
        } catch (Exception e) {
            // Catch other potential Cloudinary API errors
            log.error("Cloudinary upload failed for file: {}", originalFilename, e);
            // Wrap in a runtime exception or a custom exception
            throw new RuntimeException("Cloudinary upload failed: " + e.getMessage(), e);
        }
    }

    // Optional: Method to delete from Cloudinary using public_id
    public void deleteFile(String publicId, String resourceType) throws IOException {
        if (publicId == null || publicId.isBlank()) {
            log.warn("Attempted to delete file with null or blank publicId.");
            return;
        }
        log.info("Attempting to delete file from Cloudinary. Public ID: {}, Type: {}", publicId, resourceType);
        try {
            // Specify resource_type if it's not image (e.g., "video", "raw")
            Map<?,?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", resourceType));
            log.info("Cloudinary deletion result for {}: {}", publicId, result);
        } catch (IOException e) {
            log.error("Failed to delete file {} from Cloudinary", publicId, e);
            throw e;
        } catch (Exception e) {
            log.error("Cloudinary deletion failed for public ID: {}", publicId, e);
            throw new RuntimeException("Cloudinary deletion failed: " + e.getMessage(), e);
        }
    }
}