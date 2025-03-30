package com.chat.controller;

import com.chat.model.ChatModelCreation;
import com.chat.model.UserModel;
import com.chat.service.ChatService;
import com.chat.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true") // ✅ Apply at class level
public class UserController {

    private final UserService userService;

    private final ChatService chatService;

    @Autowired
    public UserController(UserService userService, ChatService chatService) {
        this.userService = userService;
        this.chatService = chatService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserModel user) {
        boolean isRegistered = userService.registerUser(user.getUsername(), user.getPassword());
        if (isRegistered) {
            return ResponseEntity.ok(Map.of("message", "User registered successfully!"));
        }
        return ResponseEntity.badRequest().body(Map.of("message", "User already registered!"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserModel user,HttpSession session) {
        Optional<UserModel> userOptional = userService.loginUser(user.getUsername(), user.getPassword());
        if (userOptional.isPresent()) {
            session.setAttribute("owner", userOptional.get());
            return ResponseEntity.ok(Map.of("message", "User logged in successfully!"));
        }
        return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password!"));
    }

    @GetMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out successfully!"));
    }

    @GetMapping("/session")
    public ResponseEntity<?> checkSession(HttpSession session) {
        UserModel user = (UserModel) session.getAttribute("owner");
        if (user != null) {
            return ResponseEntity.ok(Map.of("loggedIn", true, "username", user.getUsername()));
        }
        return ResponseEntity.ok(Map.of("loggedIn", false));
    }
}
