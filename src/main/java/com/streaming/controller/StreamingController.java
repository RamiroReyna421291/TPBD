package com.streaming.controller;

import com.streaming.model.Contenido;
import com.streaming.model.UserSession;
import com.streaming.service.StreamingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class StreamingController {

    @Autowired
    private StreamingService streamingService;

    @PostMapping("/contenidos")
    public ResponseEntity<Contenido> insertarContenido(@RequestBody Contenido contenido) {
        Contenido saved = streamingService.insertarContenido(contenido);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/contenidos/genero/{genero}")
    public ResponseEntity<List<Contenido>> buscarPorGenero(@PathVariable String genero) {
        List<Contenido> resultados = streamingService.buscarPorGenero(genero);
        return ResponseEntity.ok(resultados);
    }

    @PutMapping("/contenidos/{id}/metadatos")
    public ResponseEntity<Void> actualizarMetadatos(
            @PathVariable String id,
            @RequestParam String calidad) {
        streamingService.actualizarMetadatos(id, calidad);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/vistas/{contenidoId}")
    public ResponseEntity<Void> incrementVistas(@PathVariable String contenidoId) {
        streamingService.incrementVistas(contenidoId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/ranking/top5")
    public ResponseEntity<List<Map<String, Object>>> getTop5Vistas() {
        Set<ZSetOperations.TypedTuple<String>> top5 = streamingService.getTop5Vistas();
        List<Map<String, Object>> resultado = top5.stream().map(tuple -> {
            Map<String, Object> map = new HashMap<>();
            map.put("contenidoId", tuple.getValue());
            map.put("vistas", tuple.getScore());
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(resultado);
    }

    @PostMapping("/sesion/{userId}")
    public ResponseEntity<Void> guardarSesion(
            @PathVariable String userId,
            @RequestParam String contenidoId) {
        streamingService.guardarSesion(userId, contenidoId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/sesion/{userId}")
    public ResponseEntity<UserSession> obtenerSesion(@PathVariable String userId) {
        return streamingService.obtenerSesion(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}