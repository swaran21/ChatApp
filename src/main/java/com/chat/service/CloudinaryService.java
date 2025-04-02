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
// import java.util.UUID; // Keep if you plan to use custom public IDs

@Service
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    @Autowired
    private Cloudinary cloudinary;

    /**
     * Uploads a file to Cloudinary.
     *
     * @param file The file to upload.
     * @param originalFilename The original name of the file (used for metadata and potentially display).
     * @return A map containing upload details like secure_url, public_id, resource_type, bytes, and original_filename.
     * @throws IOException If reading file bytes fails.
     * @throws RuntimeException If the Cloudinary upload itself fails.
     */
    public Map<String, Object> uploadFile(MultipartFile file, String originalFilename) throws IOException {
        log.info("Attempting to upload file '{}' to Cloudinary.", originalFilename);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File to upload cannot be null or empty.");
        }
        if (originalFilename == null || originalFilename.isBlank()) {
            log.warn("Original filename is missing, using default 'upload'.");
            originalFilename = "upload"; // Provide a default if missing
        }

        try {
            // Upload options:
            // resource_type: "auto" -> Let Cloudinary detect image, video, raw
            // original_filename: Stores the original filename as metadata in Cloudinary
            // use_filename: true -> Tells Cloudinary to try and use the original filename for the public_id (it will ensure uniqueness)
            // unique_filename: false -> Works with use_filename, prevents Cloudinary from adding random characters if filename is unique enough
            // folder: "chat_uploads" -> Organize uploads in Cloudinary (optional but recommended)
            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "resource_type", "auto",
                    "original_filename", originalFilename, // Store original filename metadata
                    "use_filename", true,          // Attempt to use original filename for public ID
                    "unique_filename", false,       // Don't add random chars if use_filename works
                    "folder", "chat_uploads"      // Optional: Organize in a folder
                    // "overwrite", true             // Optional: Overwrite if file with same public_id exists
            ));

            // Extract useful information from the result
            String secureUrl = (String) uploadResult.get("secure_url"); // HTTPS URL (recommended)
            String publicIdResult = (String) uploadResult.get("public_id");
            String resourceType = (String) uploadResult.get("resource_type"); // e.g., "image", "video", "raw"
            String format = (String) uploadResult.get("format"); // e.g., "jpg", "pdf", "mp4"
            Number bytes = (Number) uploadResult.get("bytes");

            if (secureUrl == null) {
                log.error("Cloudinary upload for '{}' seemed successful but secure_url is missing. Result: {}", originalFilename, uploadResult);
                throw new RuntimeException("Cloudinary upload failed: secure_url was not returned.");
            }

            log.info("File '{}' uploaded successfully to Cloudinary. URL: {}, Public ID: {}, Type: {}, Format: {}, Size: {} bytes",
                    originalFilename, secureUrl, publicIdResult, resourceType, format, bytes);

            // Return a map containing essential details
            return Map.of(
                    "secure_url", secureUrl,
                    "public_id", publicIdResult,
                    "resource_type", resourceType,
                    "original_filename", originalFilename, // Return the name used for upload
                    "format", (format != null ? format : ""), // Return format if available
                    "bytes", (bytes != null ? bytes.longValue() : 0L)
            );

        } catch (IOException e) {
            log.error("Failed to read file bytes for Cloudinary upload: {}", originalFilename, e);
            throw e; // Re-throw IO exception
        } catch (Exception e) {
            // Catch other potential Cloudinary API errors
            log.error("Cloudinary upload failed for file: {}. Error: {}", originalFilename, e.getMessage(), e);
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
        // Default to "image" if resourceType is null/blank, but Cloudinary might need it specified for non-image types
        String typeToDelete = (resourceType != null && !resourceType.isBlank()) ? resourceType : "image";

        log.info("Attempting to delete file from Cloudinary. Public ID: {}, Resource Type: {}", publicId, typeToDelete);
        try {
            // Specify resource_type especially if it's not image (e.g., "video", "raw")
            // If type is unknown, Cloudinary might guess, but it's better to provide it.
            Map<?,?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", typeToDelete));
            log.info("Cloudinary deletion result for {}: {}", publicId, result);
            // You might want to check result.get("result") which should be "ok" or "not found"
        } catch (IOException e) {
            log.error("I/O error deleting file {} ({}) from Cloudinary", publicId, typeToDelete, e);
            throw e;
        } catch (Exception e) {
            log.error("Cloudinary API error deleting file {}: {}", publicId, e.getMessage(), e);
            throw new RuntimeException("Cloudinary deletion failed: " + e.getMessage(), e);
        }
    }
}