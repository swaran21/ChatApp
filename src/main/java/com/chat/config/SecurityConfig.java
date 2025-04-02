package com.chat.config;

import com.chat.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private UserService userService;

    // Inject the PasswordEncoder bean defined in AppConfig (or similar)
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    // AuthenticationManager Bean using the injected dependencies
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder =
                http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.userDetailsService(userService)
                .passwordEncoder(passwordEncoder);
        return authenticationManagerBuilder.build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. CORS Configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. CSRF Configuration (Disabled - Reconsider for Production)
                .csrf(AbstractHttpConfigurer::disable)

                // 3. Exception Handling (Return 401 for unauthenticated REST)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )

                // 4. Session Management
                .sessionManagement(session -> session
                        .sessionFixation().migrateSession()
                )

                // 5. Authorization Rules (Order Matters!)
                .authorizeHttpRequests(auth -> auth
                        // --- Public Endpoints ---
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/session").permitAll() // Auth operations
                        .requestMatchers("/ws-chat/**").permitAll() // WebSocket handshake

                        // --- Secured API Endpoints ---
                        .requestMatchers("/api/chat/**").authenticated() // All chat operations (create, list, delete, get messages)
                        .requestMatchers("/api/files/upload").authenticated() // Cloudinary file upload endpoint

                        // --- Default Rule ---
                        .anyRequest().authenticated() // Secure everything else by default
                )

                // 6. Logout Configuration
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout") // Define logout URL
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK))
                        .permitAll() // Allow access to the logout URL itself
                );

        return http.build();
    }

    // CORS Configuration Bean
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Adjust origin to your specific React app URL in production
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*")); // Consider restricting headers in production if needed
        configuration.setAllowCredentials(true); // Essential for cookies/session auth

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Apply this CORS config to all paths
        return source;
    }
}