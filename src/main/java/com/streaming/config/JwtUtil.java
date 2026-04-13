package com.streaming.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Utilidad para generar tokens JWT.
 * 
 * En C#, usarías bibliotecas de Microsoft para generar el Token. 
 * Aquí usamos jjwt.
 */
@Component
public class JwtUtil {

    private String secret = "streaming_secret_key"; // En producción, esto va en una variable de entorno
    private long expiration = 3600000; // 1 hora de validez

    /**
     * Genera un token JWT para un nombre de usuario.
     */
    public String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }
}
