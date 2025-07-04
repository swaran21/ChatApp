package com.chat.config;

import com.chat.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders; // Import HttpHeaders
import org.springframework.http.HttpMethod; // Import HttpMethod
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy; // Import SessionCreationPolicy
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

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    // Correct place for FRONTEND_URL injection
    @Value("${frontend.url}")
    private String frontendUrl;

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
                // Use the CORS configuration defined below
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Keep CSRF disabled for now, but review if needed for session auth
                .csrf(AbstractHttpConfigurer::disable)

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) // Return 401 if auth needed
                )

                // Ensure sessions are managed appropriately (needed for JSESSIONID)
                // Use IF_REQUIRED which is typical for REST APIs with sessions.
                .sessionManagement(session -> session
                        //.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) // Default usually okay, explicitly set if needed
                        .sessionFixation().migrateSession() // Good practice for security
                )

                .authorizeHttpRequests(auth -> auth
                        // Public endpoints: login, register, session check, WS handshake
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/session","/api/health").permitAll()
                        .requestMatchers("/ws-chat/**").permitAll() // Permit WebSocket handshake/upgrades
                        .requestMatchers("/actuator/health/**").permitAll()
                        // Secured endpoints: everything else requiring authentication
                        .requestMatchers("/api/chat/**", "/api/files/**").authenticated() // Simplified files path
                        .anyRequest().authenticated() // Default deny any other request unless authenticated
                )

                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID") // Explicitly delete cookie
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.OK))
                        .permitAll() // Allow anyone to hit the logout URL
                );

        return http.build();
    }


    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        if (frontendUrl == null || frontendUrl.isBlank()) {
            throw new IllegalStateException("Frontend URL ('frontend.url') is not configured in application properties/env.");
        }
        System.out.println("--- Configuring CORS for origin: " + frontendUrl + " ---"); // Log the configured URL

        configuration.setAllowedOrigins(List.of(frontendUrl)); // Allow specific frontend origin
        configuration.setAllowedMethods(Arrays.asList(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.OPTIONS.name() // Crucial for preflight requests
        ));
        configuration.setAllowedHeaders(Arrays.asList( // Be more explicit than "*"
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                "X-Requested-With", // Common header for AJAX
                "remember-me"       // Example header
                // Add any other custom headers your frontend might send
        ));
        configuration.setExposedHeaders(Arrays.asList(HttpHeaders.SET_COOKIE)); // Important if headers need reading by frontend
        configuration.setAllowCredentials(true); // Essential for cookies/sessions
        configuration.setMaxAge(3600L); // Cache preflight response for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Apply this config to all paths
        return source;
    }
}