package com.chat.service;

import com.chat.model.ChatModelCreation;
import com.chat.model.UserModel;
import com.chat.repo.ChatRepository;
import com.chat.repo.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User; // Import Spring Security User
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;

@Service
public class UserService implements UserDetailsService {

    private final UserRepo userRepo;

    private final BCryptPasswordEncoder passwordEncoder;

    private final ChatRepository chatRepository;

    @Value("${ai.user.id}")
    private Long aiUserId;

    @Autowired
    public UserService(UserRepo userRepo, BCryptPasswordEncoder passwordEncoder, ChatRepository chatRepository) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.chatRepository = chatRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserModel userModel = userRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        return new User(
                userModel.getUsername(),
                userModel.getPassword(),
                new ArrayList<>()
        );
    }

    public boolean registerUser(String username, String password) {
        if(userRepo.findByUsername(username).isPresent()) {
            return false;
        }
        UserModel user = new UserModel();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        UserModel savedUser = userRepo.save(user);
        createInitialAiChat(savedUser.getId());
        return true;
    }

    public Optional<UserModel> getUserModelByUsername(String username) {

        return userRepo.findByUsername(username);
    }

    private void createInitialAiChat(Long newUserId) {
        // Check if the AI user exists
        if (userRepo.existsById(aiUserId)) {
            ChatModelCreation aiChat = new ChatModelCreation();
            aiChat.setChatName("Gemini AI");
            aiChat.setOwnerId(newUserId);
            aiChat.setReceiverId(aiUserId);
            aiChat.setReceiverName("GeminiAI"); // The AI's username
            chatRepository.save(aiChat);
        }

    }
}
