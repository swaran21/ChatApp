package com.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
public class SessionConfig {

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        // Crucial for cross-domain cookie handling with HTTPS
        serializer.setSameSite("None");
        serializer.setUseSecureCookie(true); // Ensure the 'Secure' flag is set
        serializer.setUseHttpOnlyCookie(true); // Good practice - prevents JS access
        // serializer.setDomainNamePattern("^.+?\\.(\\w+\\.[a-z]+)$"); // Advanced: Use only if needed and you understand domain matching
        return serializer;
    }
}