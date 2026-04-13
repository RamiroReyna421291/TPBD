package com.streaming.config;

import com.streaming.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Utilidad JWT (Seguridad "Sin Estado" O Stateless).
 * 
 * PARADIGMA RELACIONAL vs STATELESS (JWT):
 * - Mundo Clásico (SQL): Al iniciar sesión, el backend hace un 'INSERT' a una
 *   tabla 'sesiones_activas', entregando un hash UUID al navegador. Por cada click
 *   posterior del usuario, el Servidor hace una lectura costosisíma I/O a disco:
 *   'SELECT * FROM sesiones_activas WHERE id = ...'
 *   A escala masiva (>100K users), estas lecturas estrangulan completamente el hardware de BD.
 * 
 * - Mundo JWT: No usa base de datos.
 *   Creamos un token JSON con la métrica necesaria ("ROLE_ADMIN") y lo ciframos ('signWith').
 *   El usuario nos envía la clave en cada click; EL PROCESADOR CPU DE JAVA la decifra en 
 *   nano-segundos con matemática pura. Hemos reducido la dependencia a la Base de Datos a Cero, 
 *   volviendo a la Arquitectura escalable horizontalmente de inmediato.
 */
@Component
public class JwtUtil {

    private String secret = "streaming_secret_key";
    private long expiration = 3600000; // 1 hora de validez

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("rol", user.getRol()); // Metemos el rol en el token para evitar consultas a la DB
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("rol", String.class);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
}
