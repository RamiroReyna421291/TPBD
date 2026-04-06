# Sistema de Streaming y Auditoría - Estructura de Datos NoSQL

## MongoDB (Capa de Persistencia y Metadatos)

### 1. Esquema BSON para colección `contenidos`

```json
{
  "_id": ObjectId("..."),
  "titulo": String,
  "tipo": String | Enum["PELICULA", "SERIE"],
  "generos": [String],
  "director": String,
  "anioEstreno": Number,
  "elenco": [
    {
      "nombre": String,
      "papel": String
    }
  ],
  "metadatos": {
    "calidad": String | Enum["SD", "HD", "FULL_HD", "4K"],
    "idiomas": [String],
    "subtitulos": [String]
  }
}
```

### 2. Justificación del diseño

| Campo | Tipo | Justificación |
|-------|------|---------------|
| `_id` | ObjectId | MongoDB genera ID único automáticamente |
| `tipo` | Enum | Evita valores inválidos y mejora rendimiento en búsquedas |
| `generos` | Array | Un contenido puede pertenecer a múltiples géneros |
| `elenco` | Documentos embebidos | Relación 1:N con actores; evita joins innecesarios |
| `metadatos` | Documento embebido | Información relacionada que se accede insieme |

** embebidos vs referencias**: El elenco y metadatos son documentos embebidos porque:
- Se accede junto con el contenido principal
- No requieren búsquedas independientes
- El tamaño es manejable (<16MB por documento)

### 3. Ejemplo de documento JSON válido

```json
{
  "_id": ObjectId("65f8a1b2c3d4e5f6a7b8c901"),
  "titulo": "Stranger Things",
  "tipo": "SERIE",
  "generos": ["Ciencia Ficción", "Terror", "Drama"],
  "director": "Matt Duffer",
  "anioEstreno": 2016,
  "elenco": [
    {
      "nombre": "Millie Bobby Brown",
      "papel": "Eleven"
    },
    {
      "nombre": "Finn Wolfhard",
      "papel": "Mike Wheeler"
    },
    {
      "nombre": "David Harbour",
      "papel": "Jim Hopper"
    }
  ],
  "metadatos": {
    "calidad": "4K",
    "idiomas": ["Inglés", "Español", "Portugués"],
    "subtitulos": ["Inglés", "Español", "Francés", "Alemán"]
  }
}
```

### 4. Comandos MongoDB Shell (mongosh)

#### Insertar documento de ejemplo

```javascript
use streamingDB;

db.contenidos.insertOne({
  titulo: "Stranger Things",
  tipo: "SERIE",
  generos: ["Ciencia Ficción", "Terror", "Drama"],
  director: "Matt Duffer",
  anioEstreno: 2016,
  elenco: [
    { nombre: "Millie Bobby Brown", papel: "Eleven" },
    { nombre: "Finn Wolfhard", papel: "Mike Wheeler" },
    { nombre: "David Harbour", papel: "Jim Hopper" }
  ],
  metadatos: {
    calidad: "4K",
    idiomas: ["Inglés", "Español", "Portugués"],
    subtitulos: ["Inglés", "Español", "Francés", "Alemán"]
  }
});
```

#### Buscar contenidos por género específico

```javascript
// Buscar todos los contenidos de género "Ciencia Ficción"
db.contenidos.find({ generos: "Ciencia Ficción" });

// Con proyección (solo título y año)
db.contenidos.find(
  { generos: "Ciencia Ficción" },
  { titulo: 1, anioEstreno: 1, tipo: 1, _id: 0 }
);
```

#### Actualizar campo dentro de metadatos

```javascript
// Actualizar la calidad de un contenido específico
db.contenidos.updateOne(
  { _id: ObjectId("65f8a1b2c3d4e5f6a7b8c901") },
  { $set: { "metadatos.calidad": "FULL_HD" } }
);

// Agregar un nuevo idioma a los disponibles
db.contenidos.updateOne(
  { _id: ObjectId("65f8a1b2c3d4e5f6a7b8c901") },
  { $addToSet: { "metadatos.idiomas": "Francés" } }
);
```

---

## Redis (Capa de Memoria y Tiempo Real)

### 1. Sorted Set (ZSET) para Ranking Global de Vistas

```
Clave: ranking:vistas:global
Miembro: ID del contenido (ObjectId como string)
Score: Cantidad de vistas (entero)
```

**Justificación**: El Sorted Set es ideal para rankings porque:
- Mantiene los elementos ordenados automáticamente por score
- Permite obtener rangos (top N) con ZRANGE de forma eficiente O(log(N))
- ZINCRBY permite atomicamente incrementar vistas

### 2. Estructura para Sesión de Usuario (Hash)

```
Clave: user:{id}:session
Campo: ultimo_contenido_id
Campo: timestamp
```

**Justificación**: Hash es apropiado porque:
- Almacena múltiples campos relacionados con un usuario
- Acceso O(1) a cualquier campo específico
- HMSET/HMGET son operaciones atómicas

### 3. Comandos Redis CLI

#### Incrementar contador de vistas

```bash
# Incrementar vistas del contenido específico
ZINCRBY ranking:vistas:global 1 "65f8a1b2c3d4e5f6a7b8c901"

# Obtener vista actual del contenido
ZSCORE ranking:vistas:global "65f8a1b2c3d4e5f6a7b8c901"
```

#### Obtener Top 5 contenidos más vistos

```bash
# ZREVRANGE: obtiene elementos ordenados de mayor a menor
# WITHSCORES incluye el número de vistas
ZRANKING:vistas:global 0 4 WITHSCORES

# Equivalente con WRAPPER (más legible)
ZREVRANGE ranking:vistas:global 0 4 WITHSCORES

# Obtener solo los IDs (sin scores)
ZREVRANGE ranking:vistas:global 0 4
```

#### Guardar datos de sesión de usuario

```bash
# Guardar sesión (usando Hash)
HSET user:65001 session ultimo_contenido_id "65f8a1b2c3d4e5f6a7b8c901"
HSET user:65001 session timestamp "2026-04-06T14:30:00Z"

# Alternativa: guardar ambos campos a la vez
HSET user:65001 session ultimo_contenido_id "65f8a1b2c3d4e5f6a7b8c901" timestamp "2026-04-06T14:30:00Z"

# Recuperar todos los datos de sesión
HGETALL user:65001 session

# Recuperar campo específico
HGET user:65001 session ultimo_contenido_id
```

---

## Implementación en Java (Spring Boot)

### Dependencias necesarias (pom.xml)

```xml
<dependencies>
    <!-- Spring Data MongoDB -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>
    
    <!-- Spring Data Redis -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
</dependencies>
```

### Entidad MongoDB: Contenido

```java
package com.streaming.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Document(collection = "contenidos")
public class Contenido {
    
    @Id
    private String id;
    
    @Field("titulo")
    private String titulo;
    
    @Field("tipo")
    private TipoContenido tipo;
    
    @Field("generos")
    private List<String> generos;
    
    @Field("director")
    private String director;
    
    @Field("anioEstreno")
    private Integer anioEstreno;
    
    @Field("elenco")
    private List<Actor> elenco;
    
    @Field("metadatos")
    private Metadatos metadatos;
    
    // Enum para tipo de contenido
    public enum TipoContenido {
        PELICULA,
        SERIE
    }
    
    // Clase embebida para elenco
    public static class Actor {
        private String nombre;
        private String papel;
        
        public Actor() {}
        
        public Actor(String nombre, String papel) {
            this.nombre = nombre;
            this.papel = papel;
        }
        
        // Getters y Setters
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getPapel() { return papel; }
        public void setPapel(String papel) { this.papel = papel; }
    }
    
    // Clase embebida para metadatos
    public static class Metadatos {
        private String calidad;
        private List<String> idiomas;
        private List<String> subtitulos;
        
        public Metadatos() {}
        
        // Getters y Setters
        public String getCalidad() { return calidad; }
        public void setCalidad(String calidad) { this.calidad = calidad; }
        public List<String> getIdiomas() { return idiomas; }
        public void setIdiomas(List<String> idiomas) { this.idiomas = idiomas; }
        public List<String> getSubtitulos() { return subtitulos; }
        public void setSubtitulos(List<String> subtitulos) { this.subtitulos = subtitulos; }
    }
    
    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public TipoContenido getTipo() { return tipo; }
    public void setTipo(TipoContenido tipo) { this.tipo = tipo; }
    public List<String> getGeneros() { return generos; }
    public void setGeneros(List<String> generos) { this.generos = generos; }
    public String getDirector() { return director; }
    public void setDirector(String director) { this.director = director; }
    public Integer getAnioEstreno() { return anioEstreno; }
    public void setAnioEstreno(Integer anioEstreno) { this.anioEstreno = anioEstreno; }
    public List<Actor> getElenco() { return elenco; }
    public void setElenco(List<Actor> elenco) { this.elenco = elenco; }
    public Metadatos getMetadatos() { return metadatos; }
    public void setMetadatos(Metadatos metadatos) { this.metadatos = metadatos; }
}
```

### Repositorio MongoDB

```java
package com.streaming.repository;

import com.streaming.model.Contenido;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContenidoRepository extends MongoRepository<Contenido, String> {
    
    // Buscar por género
    List<Contenido> findByGenerosContaining(String genero);
    
    // Buscar por tipo
    List<Contenido> findByTipo(Contenido.TipoContenido tipo);
    
    // Buscar por director
    List<Contenido> findByDirector(String director);
}
```

### Entidad Redis: UserSession

```java
package com.streaming.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serializable;
import java.time.Instant;

@RedisHash("user:session")
public class UserSession implements Serializable {
    
    @Id
    private String id;
    
    @Indexed
    private String userId;
    
    private String ultimoContenidoId;
    
    private Instant timestamp;
    
    public UserSession() {}
    
    public UserSession(String userId, String ultimoContenidoId) {
        this.userId = userId;
        this.ultimoContenidoId = ultimoContenidoId;
        this.timestamp = Instant.now();
    }
    
    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUltimoContenidoId() { return ultimoContenidoId; }
    public void setUltimoContenidoId(String ultimoContenidoId) { this.ultimoContenidoId = ultimoContenidoId; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
```

### Repositorio Redis para UserSession

```java
package com.streaming.repository;

import com.streaming.model.UserSession;
import org.springframework.data.redis.repository.RedisRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSessionRepository extends RedisRepository<UserSession, String> {
    
    Optional<UserSession> findByUserId(String userId);
}
```

### Servicio para Ranking de Vistas (Redis Template)

```java
package com.streaming.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class RankingService {
    
    private static final String RANKING_KEY = "ranking:vistas:global";
    
    private final RedisTemplate<String, String> redisTemplate;
    
    public RankingService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * Incrementa el contador de vistas para un contenido
     */
    public Long incrementVistas(String contenidoId) {
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        return zSetOps.incrementScore(RANKING_KEY, contenidoId, 1);
    }
    
    /**
     * Obtiene el Top N de contenidos más vistos
     */
    public Set<ZSetOperations.TypedTuple<String>> getTopN(int n) {
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        return zSetOps.reverseRangeWithScores(RANKING_KEY, 0, n - 1);
    }
    
    /**
     * Obtiene el ranking completo de vistas
     */
    public Long getVistas(String contenidoId) {
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        Double score = zSetOps.score(RANKING_KEY, contenidoId);
        return score != null ? score.longValue() : 0L;
    }
}
```

### Configuración de Redis (application.yml)

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/streamingDB
    redis:
      host: localhost
      port: 6379
```

### Configuración de Redis Template (Java Config)

```java
package com.streaming.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }
}
```

---

## Resumen de Arquitectura

```
┌─────────────────────────────────────────────────────────────┐
│                   APLICACIÓN SPRING BOOT                    │
├─────────────────────────────────────────────────────────────┤
│  Capa de Acceso a Datos                                     │
│  ┌──────────────────────┐  ┌──────────────────────────────┐ │
│  │   MongoDB            │  │   Redis                     │ │
│  │   ─────────          │  │   ──────                    │ │
│  │   Contenidos         │  │   Ranking (ZSET)            │ │
│  │   - Metadatos        │  │   User Sessions (Hash)      │ │
│  │   - Elenco           │  │                             │ │
│  └──────────────────────┘  └──────────────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│  Propósito:                                                │
│  • MongoDB: Persistencia duradera de contenido             │
│  • Redis: Tiempo real (vistas, sesiones)                   │
└─────────────────────────────────────────────────────────────┘
```
