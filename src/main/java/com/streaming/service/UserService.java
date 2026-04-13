package com.streaming.service;

import com.streaming.config.JwtUtil;
import com.streaming.model.User;
import com.streaming.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    public Map<String, Object> register(User user, boolean isAdmin) {
        if (userRepository.findByUsername(user.getUsername()).isPresent() || userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Usuario o email ya existe");
        }

        // Hashear password
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        // Asignar rol
        user.setRol(isAdmin ? "ROLE_ADMIN" : "ROLE_USER");

        User savedUser = userRepository.save(user);

        // Generar token
        String token = jwtUtil.generateToken(savedUser);

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("username", savedUser.getUsername());
        response.put("rol", savedUser.getRol());
        response.put("id", savedUser.getId());

        return response;
    }

    public Map<String, Object> login(String username, String rawPassword) {
        Optional<User> optionalUser = userRepository.findByUsername(username);
        
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (passwordEncoder.matches(rawPassword, user.getPassword())) {
                String token = jwtUtil.generateToken(user);

                Map<String, Object> response = new HashMap<>();
                response.put("token", token);
                response.put("username", user.getUsername());
                response.put("rol", user.getRol());
                response.put("id", user.getId());

                return response;
            }
        }
        
        throw new RuntimeException("Credenciales inválidas");
    }
}
