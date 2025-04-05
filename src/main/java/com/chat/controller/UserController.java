package com.chat.controller;

import com.chat.model.UserModel;
// import com.chat.model.LoginRequest; // DTO defined below or in separate file
import com.chat.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.Getter; // Lombok for DTO getters/setters
import lombok.Setter; // Lombok for DTO getters/setters
import org.slf4j.Logger; // Import Logger
import org.slf4j.LoggerFactory; // Import LoggerFactory
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException; // Specific exception
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException; // Catch specific exception
import org.springframework.security.core.context.SecurityContext; // Import SecurityContext
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository; // Standard key
import org.springframework.web.bind.annotation.*;


import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    @Autowired
    public UserController(UserService userService, AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest registerRequest) {
        if (registerRequest.getUsername() == null || registerRequest.getPassword() == null ||
                registerRequest.getUsername().isBlank() || registerRequest.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username and password cannot be empty."));
        }

        try {
            boolean isRegistered = userService.registerUser(registerRequest.getUsername(), registerRequest.getPassword());
            if (isRegistered) {
                log.info("User registered successfully: {}", registerRequest.getUsername());
                return ResponseEntity.ok(Map.of("message", "User registered successfully!"));
            } else {
                log.warn("Registration attempt failed for existing username: {}", registerRequest.getUsername());
                return ResponseEntity.status(409).body(Map.of("message", "Username already exists!"));
            }
        } catch (Exception e) {
            log.error("Error during registration for user {}: {}", registerRequest.getUsername(), e.getMessage());
            return ResponseEntity.status(500).body(Map.of("message", "Registration failed due to a server error."));
        }
    }


    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        if (loginRequest.getUsername() == null || loginRequest.getPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username and password required."));
        }

        log.info("Login attempt for user: {}", loginRequest.getUsername()); // Log attempt

        try {
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword());

            Authentication authentication = authenticationManager.authenticate(token);

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            HttpSession session = request.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            log.info("User logged in successfully: {}", userDetails.getUsername());

            return ResponseEntity.ok(Map.of(
                    "message", "User logged in successfully!",
                    "username", userDetails.getUsername()
            ));

        } catch (BadCredentialsException e) {
            log.warn("Authentication failed for user '{}': Invalid credentials", loginRequest.getUsername());
            SecurityContextHolder.clearContext();
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password!"));
        } catch (AuthenticationException e) {
            log.warn("Authentication failed for user '{}': {}", loginRequest.getUsername(), e.getMessage(), e);
            SecurityContextHolder.clearContext();
            return ResponseEntity.status(401).body(Map.of("message", "Authentication failed: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error during login for user '{}'", loginRequest.getUsername(), e);
            SecurityContextHolder.clearContext();
            return ResponseEntity.status(500).body(Map.of("message", "Login failed due to a server error."));
        }
    }

    @GetMapping("/session")
    public ResponseEntity<?> checkSession(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated() &&
                !(authentication.getPrincipal() instanceof String && "anonymousUser".equals(authentication.getPrincipal())))
        {
            String username = authentication.getName();
            log.debug("Session check successful for user: {}", username);
            return ResponseEntity.ok(Map.of("loggedIn", true, "username", username));
        } else {
            log.debug("Session check: No authenticated user found.");
            return ResponseEntity.ok(Map.of("loggedIn", false));
        }
    }
}

@Getter
@Setter
class LoginRequest {
    private String username;
    private String password;
}

@Getter
@Setter
class RegisterRequest {
    private String username;
    private String password;
}