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

@Service
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    @Autowired
    private Cloudinary cloudinary;

    public Map<String, Object> uploadFile(MultipartFile file, String originalFilename) throws IOException {
        log.info("Attempting to upload file '{}' to Cloudinary.", originalFilename);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File to upload cannot be null or empty.");
        }
        if (originalFilename == null || originalFilename.isBlank()) {
            log.warn("Original filename is missing, using default 'upload'.");
            originalFilename = "upload";
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
                    "original_filename", originalFilename,
                    "use_filename", true,
                    "unique_filename", false,
                    "folder", "chat_uploads"
            ));

            String secureUrl = (String) uploadResult.get("secure_url");
            String publicIdResult = (String) uploadResult.get("public_id");
            String resourceType = (String) uploadResult.get("resource_type");
            String format = (String) uploadResult.get("format");
            Number bytes = (Number) uploadResult.get("bytes");

            if (secureUrl == null) {
                log.error("Cloudinary upload for '{}' seemed successful but secure_url is missing. Result: {}", originalFilename, uploadResult);
                throw new RuntimeException("Cloudinary upload failed: secure_url was not returned.");
            }

            log.info("File '{}' uploaded successfully to Cloudinary. URL: {}, Public ID: {}, Type: {}, Format: {}, Size: {} bytes",
                    originalFilename, secureUrl, publicIdResult, resourceType, format, bytes);

            return Map.of(
                    "secure_url", secureUrl,
                    "public_id", publicIdResult,
                    "resource_type", resourceType,
                    "original_filename", originalFilename,
                    "format", (format != null ? format : ""),
                    "bytes", (bytes != null ? bytes.longValue() : 0L)
            );

        } catch (IOException e) {
            log.error("Failed to read file bytes for Cloudinary upload: {}", originalFilename, e);
            throw e;
        } catch (Exception e) {
            log.error("Cloudinary upload failed for file: {}. Error: {}", originalFilename, e.getMessage(), e);
            throw new RuntimeException("Cloudinary upload failed: " + e.getMessage(), e);
        }
    }

    public void deleteFile(String publicId, String resourceType) throws IOException {
        if (publicId == null || publicId.isBlank()) {
            log.warn("Attempted to delete file with null or blank publicId.");
            return;
        }
        String typeToDelete = (resourceType != null && !resourceType.isBlank()) ? resourceType : "image";

        log.info("Attempting to delete file from Cloudinary. Public ID: {}, Resource Type: {}", publicId, typeToDelete);
        try {
            Map<?,?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", typeToDelete));
            log.info("Cloudinary deletion result for {}: {}", publicId, result);
        } catch (IOException e) {
            log.error("I/O error deleting file {} ({}) from Cloudinary", publicId, typeToDelete, e);
            throw e;
        } catch (Exception e) {
            log.error("Cloudinary API error deleting file {}: {}", publicId, e.getMessage(), e);
            throw new RuntimeException("Cloudinary deletion failed: " + e.getMessage(), e);
        }
    }
}