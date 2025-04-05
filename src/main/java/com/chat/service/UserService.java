package com.chat.service;

import com.chat.model.UserModel;
import com.chat.repo.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public UserService(UserRepo userRepo, BCryptPasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
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
        userRepo.save(user);
        return true;
    }

    public Optional<UserModel> getUserModelByUsername(String username) {

        return userRepo.findByUsername(username);
    }


}
