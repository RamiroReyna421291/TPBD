package com.streaming.service;

import com.streaming.model.Contenido;
import com.streaming.model.UserSession;
import com.streaming.repository.ContenidoRepository;
import com.streaming.repository.UserSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Servicio principal de la aplicación de streaming.
 * 
 * Este servicio coordina dos bases de datos:
 * - MongoDB: para persistencia de contenidos (a través de ContenidoRepository)
 * - Redis: para datos efímeros y de alta velocidad (Ranking y Sesiones)
 * 
 * La separación es intencional: MongoDB para datos que necesitan persistencia
 * a largo plazo, Redis para operaciones rápidas en memoria.
 */
@Service
public class StreamingService {

    // =================================================================
    // CONSTANTES - Keys de Redis
    // =================================================================
    
    /**
     * Key del Sorted Set que guarda el ranking global de vistas.
     * Un Sorted Set es una estructura de datos donde cada elemento tiene
     * un "score" (número) asociado, y los elementos se ordenan por ese score.
     * 
     * Estructura en Redis:
     *   Key: "ranking:vistas:global"
     *   Miembros: contenidoId1, contenidoId2, contenidoId3, ...
     *   Scores: 1500, 1200, 800, ... (cantidad de vistas)
     * 
     * Orden: Los elementos se ordenan ASCENDENTE por score.
     * Para obtener los más vistos, usamos reverseRange (mayor a menor).
     */
    private static final String RANKING_KEY = "ranking:vistas:global";

    // =================================================================
    // DEPENDENCIAS - Inyectadas por Spring
    // =================================================================
    
    /**
     * Repository para operaciones CRUD en MongoDB.
     * Spring Data MongoDB provee implementación automática
     * a través de MongoRepository.
     */
    @Autowired
    private ContenidoRepository contenidoRepository;
    
    /**
     * Repository para operaciones con sesiones en Redis.
     * Este wrapper nos permite usar operaciones de alto nivel
     * sobre Hashes de Redis sin preocuparnos por los detalles.
     */
    @Autowired
    private UserSessionRepository userSessionRepository;

    /**
     * ZSetOperations permite operar con Sorted Sets de Redis.
     * Un Sorted Set es ideal para rankings porque:
     * - Insertar/incrementar: O(log N)
     * - Obtener top N: O(log N + N)
     * - Ordenamiento automático por score
     * 
     * Inyectado por Spring Boot automáticamente gracias a RedisConfig.
     */
    @Autowired
    private ZSetOperations<String, String> zSetOperations;

    /**
     * RedisTemplate es el objeto de bajo nivel para operaciones con Redis.
     * Lo usamos para operaciones que no tienen un wrapper específico.
     * 
     * NOTA: En un código más limpio, preferiríamos usar los repositories,
     * pero ZSetOperations no tiene un repository equivalent en Spring Data.
     */
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    // =================================================================
    // OPERACIONES MONGODB - Contenidos
    // =================================================================

    /**
     * Inserta un nuevo contenido en MongoDB.
     * 
     * MongoDB generará automáticamente un ObjectId como _id
     * si no proporcionamos uno explícitamente.
     * 
     * Equivalente SQL:
     *   INSERT INTO contenidos (titulo, tipo, generos, director, anioEstreno)
     *   VALUES (?, ?, ?, ?, ?)
     * 
     * @param contenido el contenido a insertar
     * @return el contenido con su ID asignado
     */
    public Contenido insertarContenido(Contenido contenido) {
        return contenidoRepository.save(contenido);
    }

    /**
     * Obtiene todos los contenidos del catálogo.
     * 
     * Equivalente SQL: SELECT * FROM contenidos
     * 
     * @return lista de todos los contenidos
     */
    public List<Contenido> obtenerTodosContenidos() {
        return contenidoRepository.findAll();
    }

    /**
     * Busca un contenido específico por su ID.
     * 
     * @param id ID único autogenerado por MongoDB
     * @return Optional con el contenido si existe
     */
    public Optional<Contenido> obtenerContenidoPorId(String id) {
        return contenidoRepository.findById(id);
    }

    /**
     * Reemplaza el documento completo de un contenido.
     * 
     * IMPORTANTE: En MongoDB el método save() actúa como Upsert.
     * Si el ID ya existe, pisa todo el documento con la nueva data.
     * 
     * @param id ID del contenido a actualizar
     * @param contenido Datos nuevos del documento
     * @return El contenido actualizado
     */
    public Contenido actualizarContenido(String id, Contenido contenido) {
        contenido.setId(id);
        return contenidoRepository.save(contenido);
    }

    /**
     * Elimina un contenido físicamente de MongoDB.
     * 
     * @param id ID del contenido a eliminar
     */
    public void eliminarContenido(String id) {
        contenidoRepository.deleteById(id);
    }

    /**
     * Busca contenidos por género.
     * 
     * MongoDB busca en el array "generos" elementos que contengan
     * el string especificado (equals simple, no regex).
     * 
     * Equivalente SQL:
     *   SELECT * FROM contenidos c
     *   JOIN contenidos_generos cg ON c.id = cg.contenido_id
     *   JOIN generos g ON cg.genero_id = g.id
     *   WHERE g.nombre = ?
     * 
     * @param genero el género a buscar
     * @return lista de contenidos que tienen ese género
     */
    public List<Contenido> buscarPorGenero(String genero) {
        return contenidoRepository.findByGenerosContaining(genero);
    }

    /**
     * Actualiza los metadatos de un contenido.
     * 
     * En MongoDB, los documentos embebidos se actualizan con dot notation.
     * Por ejemplo: "metadatos.calidad" accede al campo calidad dentro de metadatos.
     * 
     * Este método:
     * 1. Busca el contenido por ID
     * 2. Modifica el campo calidad dentro de metadatos
     * 3. Guarda el documento actualizado (MongoDB hace el replace)
     * 
     * Equivalente SQL:
     *   UPDATE contenidos c
     *   JOIN metadatos m ON c.id = m.contenido_id
     *   SET m.calidad = ?
     *   WHERE c.id = ?
     * 
     * @param contenidoId ID del contenido a actualizar
     * @param calidad nueva calidad (ej: "4K", "1080p", "720p")
     */
    public void actualizarMetadatos(String contenidoId, String calidad) {
        Optional<Contenido> opt = contenidoRepository.findById(contenidoId);
        if (opt.isPresent()) {
            Contenido contenido = opt.get();
            // Verificar que tenga metadatos antes de modificar
            if (contenido.getMetadatos() != null) {
                contenido.getMetadatos().setCalidad(calidad);
                // save() en MongoDB hace un update/replace del documento completo
                contenidoRepository.save(contenido);
            }
        }
        // Si el contenido no existe, no hacemos nada (silencioso)
        // Podríamos lanzar una excepción en un caso real
    }

    // =================================================================
    // OPERACIONES REDIS - RANKING (Sorted Set)
    // =================================================================

    /**
     * Incrementa el contador de vistas de un contenido.
     * 
     * Esta es una de las operaciones más poderosas de Redis:
     * ZINCRBY es ATÓMICO, lo que significa que even si 1000 usuarios
     * llaman este método simultáneamente, el contador se incrementará
     * correctamente sin race conditions.
     * 
     * Comando Redis ejecutado:
     *   ZINCRBY ranking:vistas:global 1 "contenido123"
     * 
     * Cómo funciona internamente:
     * 1. Busca "contenido123" en el sorted set
     * 2. Si existe: nuevo_score = score_actual + 1
     * 3. Si no existe: nuevo_score = 1 (nuevo elemento)
     * 4. Reordena el set según el nuevo score
     * 
     * Complejidad: O(log N) donde N es el número de contenidos
     * 
     * ¿Por qué no usar SQL para esto?
     * - En SQL necesitarías: UPDATE ranking SET vistas = vistas + 1
     * - Problema: si dos usuarios ejecutan simultáneamente, podés perder actualizaciones
     * - Solución SQL: SELECT FOR UPDATE o transacciones, más lento
     * - Solución Redis: ZINCRBY es atómico por diseño
     * 
     * @param contenidoId ID del contenido cuyas vistas incrementamos
     */
    public void incrementVistas(String contenidoId) {
        /**
         * incrementScore(member, delta)
         * - member: el elemento en el sorted set (ID del contenido)
         * - delta: cuánto incrementar (1 en este caso)
         * 
         * También podés usarlo para decrementar pasando delta negativo:
         *   zSetOperations.incrementScore(RANKING_KEY, contenidoId, -1);
         * 
         * Return: el nuevo score del elemento
         */
        zSetOperations.incrementScore(RANKING_KEY, contenidoId, 1);
    }

    /**
     * Obtiene los 5 contenidos más vistos.
     * 
     * Usa reverseRangeWithScores para obtener los elementos
     * desde el score más alto (0) hasta el 5to (4).
     * 
     * Comando Redis ejecutado:
     *   ZREVRANGE ranking:vistas:global 0 4 WITHSCORES
     * 
     * Diferencia entre range y reverseRange:
     * - range(start, end): menor a mayor (score ascendente)
     * - reverseRange(start, end): mayor a menor (score descendente)
     * 
     * Complejidad: O(log N + N) donde N es el rango solicitado (5)
     * 
     * @return Set de tuplas (contenidoId, score) ordenados por vistas (mayor a menor)
     */
    public Set<ZSetOperations.TypedTuple<String>> getTop5Vistas() {
        /**
         * reverseRangeWithScores(key, start, end)
         * - start: índice inicial (0 = el primero, el de mayor score)
         * - end: índice final (4 = quinto elemento)
         * - withScores: incluir los scores en el resultado
         * 
         * TypedTuple<String> contiene:
         *   - getValue(): el ID del contenido
         *   - getScore(): la cantidad de vistas (como Double)
         * 
         * Si quieres solo los IDs sin scores, usa reverseRange(key, 0, 4)
         */
        return zSetOperations.reverseRangeWithScores(RANKING_KEY, 0, 4);
    }

    // =================================================================
    // OPERACIONES REDIS - SESIONES (Hash)
    // =================================================================

    /**
     * Guarda la sesión de un usuario en Redis.
     * 
     * Una sesión representa el último contenido que el usuario estaba viendo.
     * Usamos Redis Hash porque:
     * - Una sola key para múltiples campos
     * - TTL automático para expirar sesiones
     * - Lectura atómica de todos los campos
     * 
     * Estructura en Redis:
     *   Key: "user:session:user123"
     *   Hash: {
     *     "lastContentId": "contenido456",
     *     "timestamp": "1751821800"
     *   }
     *   TTL: 3600 segundos (1 hora)
     * 
     * Comandos Redis ejecutados:
     *   HSET user:session:user123 lastContentId "contenido456" timestamp "1751821800"
     *   EXPIRE user:session:user123 3600
     * 
     * ¿Por qué no usar MongoDB para sesiones?
     * - Las sesiones son efímeras: queremos que expiren automáticamente
     * - Acceso muy frecuente: cada vez que el usuario hace una acción
     * - Datos simples: no necesitamos la complejidad de un documento completo
     * - TTL nativo: MongoDB puede hacerlo pero es más complejo
     * 
     * @param userId ID del usuario
     * @param contenidoId ID del contenido que estaba viendo
     */
    public void guardarSesion(String userId, String contenidoId) {
        /**
         * Crear el objeto UserSession con los datos a guardar.
         * El timestamp se guarda como Unix timestamp (segundos desde 1970).
         * Esto es más fácil de manejar que Dates porque no tiene timezone.
         */
        Long timestamp = System.currentTimeMillis() / 1000;
        UserSession session = new UserSession(userId, contenidoId, timestamp);
        
        /**
         * Delegar al repository que maneja la complejidad de:
         * - Construir la key correcta
         * - Serializar los campos
         * - Establecer el TTL
         */
        userSessionRepository.save(session);
    }

    /**
     * Obtiene la sesión de un usuario.
     * 
     * Devuelve el último contenido que el usuario estaba viendo,
     * junto con el timestamp de cuándo fue la última actividad.
     * 
     * Si la sesión no existe o ya expiró, devuelve Optional.empty().
     * 
     * Comando Redis ejecutado:
     *   HGETALL user:session:user123
     * 
     * @param userId ID del usuario
     * @return Optional con la sesión si existe y no expiró
     */
    public Optional<UserSession> obtenerSesion(String userId) {
        /**
         * El repository se encarga de:
         * 1. Construir la key "user:session:" + userId
         * 2. Ejecutar HGETALL para obtener todos los campos
         * 3. Manejar el caso de sesión inexistente (Map vacío)
         * 4. Convertir los campos de vuelta a un objeto UserSession
         */
        return userSessionRepository.findByUserId(userId);
    }

    /**
     * Elimina explícitamente la sesión de un usuario (Logout manual).
     * 
     * Comando Redis ejecutado: DEL user:session:{userId}
     * 
     * @param userId ID del usuario
     */
    public void eliminarSesion(String userId) {
        userSessionRepository.delete(userId);
    }
}