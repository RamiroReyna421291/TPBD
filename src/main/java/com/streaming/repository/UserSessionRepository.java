package com.streaming.repository;

import com.streaming.model.UserSession;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Repository para sesiones de usuario en Redis usando Hash.
 * 
 * Este repository usa RedisTemplate directamente para operar con Hashes.
 * Un Hash en Redis es como un objeto JSON almacenado en una sola key,
 * con múltiples campos y valores.
 * 
 * Estructura en Redis:
 *   Key: "user:session:{userId}"
 *   Hash fields: { "lastContentId": "...", "timestamp": "..." }
 * 
 * Ventajas del Hash:
 * - Una sola key para todos los campos de la sesión
 * - TTL (Time To Live) automático para expirar sesiones
 * - Lectura atómica de todos los campos con HGETALL
 */
@Repository
public class UserSessionRepository {

    // =================================================================
    // CONSTANTES - Definen la estructura de datos en Redis
    // =================================================================
    
    /**
     * Prefijo común para todas las claves de sesión.
     * Todas las sesiones se guardarán con keys del tipo: "user:session:user123"
     */
    private static final String KEY_PREFIX = "user:session:";
    
    /**
     * Nombre del campo que guarda el último contenido visto.
     */
    private static final String FIELD_LAST_CONTENT = "lastContentId";
    
    /**
     * Nombre del campo que guarda el timestamp de la sesión.
     */
    private static final String FIELD_TIMESTAMP = "timestamp";
    
    /**
     * TTL por defecto: 3600 segundos = 1 hora.
     * Después de este tiempo, Redis elimina automáticamente la sesión.
     */
    private static final long DEFAULT_TTL_SECONDS = 3600;

    // =================================================================
    // DEPENDENCIAS
    // =================================================================
    
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Constructor con inyección de dependencias.
     * RedisTemplate es proporcionado por Spring Boot automáticamente
     * gracias a la dependencia spring-boot-starter-data-redis.
     * 
     * RedisTemplate es el objeto principal para interactuar con Redis.
     * Tiene serializers configurados (StringRedisSerializer en tu config)
     * que convierten objetos Java a strings y viceversa.
     */
    public UserSessionRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // =================================================================
    // OPERACIONES CRUD
    // =================================================================

    /**
     * Guarda una sesión de usuario en Redis.
     * 
     * Qué pasa internamente:
     * 1. Se construye la key completa: "user:session:" + userId
     * 2. Se crea un Map con los campos y valores a guardar
     * 3. Se ejecuta HSET (Hash Set) para guardar los campos
     * 4. Se ejecuta EXPIRE para establecer el TTL
     * 
     * Comandos Redis ejecutados:
     *   HSET user:session:user123 lastContentId "contenido456" timestamp "1751821800"
     *   EXPIRE user:session:user123 3600
     * 
     * @param session la sesión a guardar (contiene userId, lastContentId, timestamp)
     */
    public void save(UserSession session) {
        // 1. Construir la key específica para este usuario
        String key = KEY_PREFIX + session.getUserId();
        
        // 2. Crear mapa con los campos a guardar
        // Redis Hash solo acepta String como clave y valor
        java.util.Map<String, String> hashMap = new java.util.HashMap<>();
        hashMap.put(FIELD_LAST_CONTENT, session.getLastContentId());
        hashMap.put(FIELD_TIMESTAMP, String.valueOf(session.getTimestamp()));
        
        // 3. Guardar todos los campos en el hash con una sola operación
        // HSET permite guardar múltiples campos a la vez
        redisTemplate.opsForHash().putAll(key, hashMap);
        
        // 4. Establecer tiempo de expiración (TTL)
        // Después de DEFAULT_TTL_SECONDS segundos, Redis elimina la key automáticamente
        // Esto es CRUCIAL para sesiones: no querés sesiones obsoletas ocupando memoria
        redisTemplate.expire(key, DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Busca una sesión de usuario por su userId.
     * 
     * Qué pasa internamente:
     * 1. Se construye la key: "user:session:" + userId
     * 2. Se verifica si la key existe (sesión no expirada)
     * 3. Se leen todos los campos del hash con HGETALL
     * 4. Se construye un objeto UserSession con los datos obtenidos
     * 
     * Comandos Redis ejecutados:
     *   EXISTS user:session:user123  (verifica si existe)
     *   HGETALL user:session:user123  (obtiene todos los campos)
     * 
     * @param userId el ID del usuario a buscar
     * @return Optional con la sesión si existe y no expiró, empty si no existe
     */
    public Optional<UserSession> findByUserId(String userId) {
        // 1. Construir la key
        String key = KEY_PREFIX + userId;
        
        // 2. Obtener todos los campos del hash
        // HGETALL devuelve un Map<String, String> con todos los campos
        // Si la key no existe, devuelve un Map vacío
        java.util.Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        
        // 3. Si el mapa está vacío, la sesión no existe o expiró
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        
        // 4. Extraer los valores de los campos
        // Los valores en el Map son Object, hay que castear a String
        String lastContentId = (String) entries.get(FIELD_LAST_CONTENT);
        String timestampStr = (String) entries.get(FIELD_TIMESTAMP);
        
        // 5. Convertir el timestamp de String a Long
        // Si timestampStr es null, usar 0 como valor por defecto
        Long timestamp = timestampStr != null ? Long.parseLong(timestampStr) : null;
        
        // 6. Devolver la sesión envuelta en Optional
        return Optional.of(new UserSession(userId, lastContentId, timestamp));
    }

    /**
     * Actualiza la sesión de un usuario, extendiendo el TTL.
     * 
     * Esta operación es útil para "renovar" una sesión cuando el usuario
     * está activo, manteniendo su sesión viva sin crear una nueva.
     * 
     * Comandos Redis ejecutados:
     *   HSET user:session:user123 lastContentId "nuevoContenido" timestamp "1751821800"
     *   EXPIRE user:session:user123 3600  (se resetea el TTL)
     * 
     * @param session la sesión con los datos actualizados
     */
    public void update(UserSession session) {
        // La operación save() ya hace HSET + EXPIRE
        // así que simplemente llamar a save() actualiza y renueva el TTL
        save(session);
    }

    /**
     * Elimina una sesión de usuario.
     * 
     * Útil para logout explícito (aunque el TTL ya se encarga de limpiar).
     * 
     * Comando Redis ejecutado:
     *   DEL user:session:user123
     * 
     * @param userId el ID del usuario cuya sesión se eliminará
     */
    public void delete(String userId) {
        String key = KEY_PREFIX + userId;
        redisTemplate.delete(key);
    }

    /**
     * Verifica si existe una sesión activa para un usuario.
     * 
     * Comando Redis ejecutado:
     *   EXISTS user:session:user123
     * 
     * @param userId el ID del usuario
     * @return true si existe y no ha expirado, false otherwise
     */
    public boolean exists(String userId) {
        String key = KEY_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Renueva el TTL de una sesión (para mantenerla activa).
     * 
     * Cuando un usuario está activo, podés llamar esto para extender
     * su sesión sin necesidad de guardar todos los datos de nuevo.
     * 
     * Comando Redis ejecutado:
     *   EXPIRE user:session:user123 3600
     * 
     * @param userId el ID del usuario
     */
    public void renewTTL(String userId) {
        String key = KEY_PREFIX + userId;
        redisTemplate.expire(key, DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
    }
}