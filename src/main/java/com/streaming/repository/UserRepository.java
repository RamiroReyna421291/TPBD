package com.streaming.repository;

import com.streaming.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Repositorio de Usuarios (MongoDB).
 * 
 * Sigue el mismo paradigma de inversión de control que el catálogo.
 * Nótese que ni Mongo ni el Repositorio conocen la lógica de la Autenticación
 * ni administran sesiones activas online. Operan atómicamente retornando usuarios.
 */
@Repository
public interface UserRepository extends MongoRepository<User, String> {
    /**
     * Equivalente SQL Clásico:
     *   SELECT * FROM usuarios WHERE username = ? LIMIT 1
     * 
     * En vez de devolver Null (Típico del JDBC Relacional), devuelve Optional<User>,
     * una bóveda que forza al Arquitecto de Java a prever programáticamente el 
     * caso donde el usuario no exista, erradicando los NullPointerException del backend.
     */
    Optional<User> findByUsername(String username);
    /**
     * Altamente optimizado: Spring Data emite el comando 'db.collection.countDocuments(...)'
     * deteniendo la iteración en disco del motor Mongo a la primera coincidencia (True/False limit).
     * Más liviano que abstraer todo el JSON del usuario si solo validamos un email pre-existente.
     */
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
}
