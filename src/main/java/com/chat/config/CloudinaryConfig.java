// src/main/java/com/chat/config/CloudinaryConfig.java
package com.chat.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class CloudinaryConfig {

    @Value("${cloudinary.cloud_name}")
    private String cloudName;

    @Value("${cloudinary.api_key}")
    private String apiKey;

    @Value("${cloudinary.api_secret}")
    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        // Input validation for credentials
        if (cloudName == null || cloudName.isBlank() ||
                apiKey == null || apiKey.isBlank() ||
                apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalArgumentException("Cloudinary credentials (cloud_name, api_key, api_secret) must be set in application properties.");
        }

        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", cloudName);
        config.put("api_key", apiKey);
        config.put("api_secret", apiSecret);
        config.put("secure", "true"); // Force HTTPS URLs, recommended
        return new Cloudinary(config);
    }
}