package com.chat.service;

import com.chat.model.UserModel;
import com.chat.repo.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepo userRepo;

    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepo userRepo, BCryptPasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
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

    public Optional<UserModel> loginUser(String username, String password) {
        Optional<UserModel> user = userRepo.findByUsername(username);
        return user.filter(u -> passwordEncoder.matches(password, u.getPassword()));
    }

}
