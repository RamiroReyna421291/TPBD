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
    public User register(String username, String password, String email, String country) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("El nombre de usuario ya existe");
        }
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("El email ya está registrado");
        }

        // Hasheamos la contraseña antes de guardar
        String encodedPassword = passwordEncoder.encode(password);
        User newUser = new User(username, email, encodedPassword, country);
        
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

            // 3. Crear sesión en Redis (inicializar si no existe o renovar)
            Long timestamp = System.currentTimeMillis() / 1000;
            
            // Intentamos recuperar si ya había una sesión para mantener el lastContentId
            Optional<UserSession> existingSession = userSessionRepository.findByUserId(user.getId());
            String lastContentId = existingSession.map(UserSession::getLastContentId).orElse(null);
            
            UserSession session = new UserSession(user.getId(), lastContentId, timestamp);
            userSessionRepository.save(session);

            // 4. Generar Token JWT
            String token = jwtUtil.generateToken(user.getUsername());

            // Preparamos la respuesta
            Map<String, Object> response = new HashMap<>();
            response.put("user", user);
            response.put("token", token);
            response.put("session", session);

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

    /**
     * Obtiene el perfil completo del usuario, incluyendo su sesión actual en Redis.
     * 
     * Este es un ejemplo de cómo coordinar datos de MongoDB y Redis.
     */
    public Optional<Map<String, Object>> getUserProfile(String userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            Optional<UserSession> sessionOpt = userSessionRepository.findByUserId(userId);
            
            Map<String, Object> profile = new HashMap<>();
            profile.put("user", user);
            profile.put("session", sessionOpt.orElse(null));
            
            return Optional.of(profile);
        }
        
        return Optional.empty();
    }
}
