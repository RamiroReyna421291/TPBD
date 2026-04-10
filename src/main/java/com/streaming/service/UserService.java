package com.streaming.service;

import com.streaming.config.JwtUtil;
import com.streaming.model.User;
import com.streaming.model.UserSession;
import com.streaming.repository.UserRepository;
import com.streaming.repository.UserSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio para la gestión de usuarios y autenticación segura.
 */
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * Registra un nuevo usuario con contraseña hasheada (BCrypt).
     */
    public User register(String username, String password, String email) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("El nombre de usuario ya existe");
        }
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("El email ya está registrado");
        }

        // Hasheamos la contraseña antes de guardar
        String encodedPassword = passwordEncoder.encode(password);
        User newUser = new User(username, encodedPassword, email);
        
        return userRepository.save(newUser);
    }

    /**
     * Inicia sesión, verifica BCrypt y genera Token JWT.
     * 
     * Retorna un mapa con el usuario y el token generado.
     */
    public Optional<Map<String, Object>> login(String username, String password) {
        // 1. Buscar usuario en MongoDB
        Optional<User> userOpt = userRepository.findByUsername(username);

        // 2. Validar credenciales con BCrypt (matches)
        if (userOpt.isPresent() && passwordEncoder.matches(password, userOpt.get().getPassword())) {
            User user = userOpt.get();

            // 3. Crear sesión en Redis
            Long timestamp = System.currentTimeMillis() / 1000;
            UserSession session = new UserSession(user.getId(), null, timestamp);
            userSessionRepository.save(session);

            // 4. Generar Token JWT
            String token = jwtUtil.generateToken(user.getUsername());

            // Preparamos la respuesta
            Map<String, Object> response = new HashMap<>();
            response.put("user", user);
            response.put("token", token);

            return Optional.of(response);
        }

        return Optional.empty();
    }

    /**
     * Cierra la sesión en Redis.
     */
    public void logout(String userId) {
        userSessionRepository.delete(userId);
    }
}
