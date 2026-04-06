package com.streaming.service;

import com.streaming.model.Contenido;
import com.streaming.model.UserSession;
import com.streaming.repository.ContenidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class StreamingService {

    private static final String RANKING_KEY = "ranking:vistas:global";
    private static final long SESSION_TTL = 3600;

    @Autowired
    private ContenidoRepository contenidoRepository;

    @Autowired
    private ZSetOperations<String, String> zSetOperations;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public Contenido insertarContenido(Contenido contenido) {
        return contenidoRepository.save(contenido);
    }

    public List<Contenido> buscarPorGenero(String genero) {
        return contenidoRepository.findByGenerosContaining(genero);
    }

    public void actualizarMetadatos(String contenidoId, String calidad) {
        Optional<Contenido> opt = contenidoRepository.findById(contenidoId);
        if (opt.isPresent()) {
            Contenido contenido = opt.get();
            if (contenido.getMetadatos() != null) {
                contenido.getMetadatos().setCalidad(calidad);
            }
            contenidoRepository.save(contenido);
        }
    }

    public void incrementVistas(String contenidoId) {
        zSetOperations.incrementScore(RANKING_KEY, contenidoId, 1);
    }

    public Set<ZSetOperations.TypedTuple<String>> getTop5Vistas() {
        return zSetOperations.reverseRangeWithScores(RANKING_KEY, 0, 4);
    }

    public void guardarSesion(String userId, String contenidoId) {
        String key = "user:" + userId + ":session";
        Long timestamp = System.currentTimeMillis() / 1000;
        redisTemplate.opsForHash().put(key, "lastContentId", contenidoId);
        redisTemplate.opsForHash().put(key, "timestamp", String.valueOf(timestamp));
        redisTemplate.expire(key, SESSION_TTL, TimeUnit.SECONDS);
    }

    public Optional<UserSession> obtenerSesion(String userId) {
        String key = "user:" + userId + ":session";
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        String lastContentId = (String) entries.get("lastContentId");
        String ts = (String) entries.get("timestamp");
        Long timestamp = ts != null ? Long.parseLong(ts) : null;
        return Optional.of(new UserSession(userId, lastContentId, timestamp));
    }
}