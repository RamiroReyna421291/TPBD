package com.streaming.controller;

import com.streaming.model.User;
import com.streaming.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Controlador de Autenticación.
 * 
 * En C#, esto sería un [ApiController] con [Route("api/auth")].
 * En Spring Boot usamos @RestController y @RequestMapping.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    /**
     * Registro de usuario.
     * Espera un JSON con username, password, email.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        try {
            User registeredUser = userService.register(
                user.getUsername(), 
                user.getPassword(), 
                user.getEmail(),
                user.getCountry()
            );
            return ResponseEntity.ok(registeredUser);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Login de usuario.
     * Retorna el objeto Usuario y el Token JWT si el login es exitoso.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        Optional<Map<String, Object>> loginResponse = userService.login(username, password);

        if (loginResponse.isPresent()) {
            return ResponseEntity.ok(loginResponse.get());
        } else {
            return ResponseEntity.status(401).body("Credenciales inválidas");
        }
    }

    /**
     * Logout.
     */
    @PostMapping("/logout/{userId}")
    public ResponseEntity<?> logout(@PathVariable String userId) {
        userService.logout(userId);
        return ResponseEntity.ok("Sesión cerrada");
    }

    /**
     * Obtiene el perfil completo del usuario.
     */
    @GetMapping("/profile/{userId}")
    public ResponseEntity<?> getProfile(@PathVariable String userId) {
        return userService.getUserProfile(userId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
