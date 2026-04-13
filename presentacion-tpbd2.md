# Presentación del Proyecto TPBD2: Plataforma de Streaming

## Materia: Bases de Datos No Relacionales

---

## 1. Introducción y Contexto del Proyecto

### 1.1 Descripción General

Este proyecto es una **plataforma de streaming** desarrollada con Spring Boot que integra dos bases de datos NoSQL: **MongoDB** para persistencia de datos y **Redis** para operaciones de alta velocidad. La arquitectura demuestra el concepto de **Polyglot Persistence**, donde cada base de datos se utiliza para el propósito para el cual fue diseñada.

### 1.2 Tecnologías Utilizadas

| Tecnología | Propósito | Versión |
|------------|-----------|---------|
| Spring Boot | Framework principal | 2.x |
| Spring Data MongoDB | Persistencia de datos | - |
| Spring Data Redis | Cache y operaciones rápidas | - |
| MongoDB | Base de datos documental | latest |
| Redis | Almacén clave-valor | latest |
| Docker | Contenerización | 3.8 |
| JWT | Autenticación sin estado | 0.9.1 |

---

## 2. Docker: La Base de la Infraestructura

### 2.1 ¿Por Qué Docker?

Docker permite **empaquetar la aplicación junto con todas sus dependencias**, garantizando que el código funcione de manera idéntica en cualquier entorno. En el contexto de bases de datos NoSQL, esto es especialmente importante porque:

1. **Reproducibilidad**: Todos los desarrolladores trabajan con las mismas versiones de MongoDB y Redis
2. **Aislamiento**: Cada servicio corre en su propio contenedor sin conflictos
3. **Simplicidad**: Un solo comando levanta toda la infraestructura
4. **Portabilidad**: Funciona en cualquier máquina con Docker instalado

### 2.2 Configuración Docker Compose

```yaml
version: '3.8'

services:
  # MongoDB: Base de datos documental para Usuarios y Contenidos
  mongodb:
    image: mongo:latest
    container_name: streaming-mongodb
    ports:
      - "27017:27017"
    volumes:
      - mongo-data:/data/db

  # Redis: Almacén clave-valor para Sesiones y Ranking
  redis:
    image: redis:latest
    container_name: streaming-redis
    ports:
      - "6379:6379"
    command: redis-server --save 60 1 --loglevel warning

volumes:
  mongo-data:
```

### 2.3 Análisis de Cada Línea

**`version: '3.8'`**: Define la versión del formato de Docker Compose. La versión 3.8 soporta todas las características modernas incluyendo despliegues en swarm.

**`services:`**: Define los contenedores que queremos levantar. Es el núcleo de la orquestación.

**`mongodb:`**: Nombre del servicio que será usado internamente por otros contenedores para comunicarse.

**`image: mongo:latest`**: Indica Docker que debe descargar la última imagen disponible de MongoDB desde Docker Hub. Esta imagen contiene el servidor de MongoDB preconfigurado.

**`container_name: streaming-mongodb`**: Asigna un nombre legible al contenedor en lugar del nombre aleatorio que Docker genera por defecto.

**`ports: - "27017:27017"`**: Mapea el puerto 27017 del contenedor al puerto 27017 del host. El puerto 27017 es el puerto padrão de MongoDB.

**`volumes: - mongo-data:/data/db`**: Crea un volumen Docker llamado `mongo-data` y lo monta en `/data/db` dentro del contenedor. Esto asegura que **los datos persisten incluso si el contenedor se destruye**. Sin esto, al eliminar el contenedor se pierden todos los datos.

**`redis:`**: Segundo servicio, idéntica estructura pero para Redis.

**`command: redis-server --save 60 1 --loglevel warning`**: Sobreescribe el comando por defecto de la imagen Redis.

**Análisis del comando:**
- `--save 60 1`: Crea un snapshot RDB cada 60 segundos si al menos 1 clave cambió
- `--loglevel warning`: Reduce logs para mejorar rendimiento

### 2.3.1 Redis y la Persistencia: ¿Redis no guarda información?

Esta es una confusión común que vamos a aclarar:

**Redis es PRIMARIAMENTE un almacén en memoria**, pero **PUEDE persistir datos** de forma opcional. Es como un auto de carrera: está diseñado para máxima velocidad (memoria RAM), pero tiene un baúl (persistencia) por si necesitás mudar algo.

#### Los dos modos de persistencia en Redis:

| Modo | Cómo funciona | Cuándo usarlo |
|------|---------------|---------------|
| **RDB (Snapshot)** | Cada X tiempo, guarda TODO en un archivo `dump.rdb` | Datos que pueden regenerarse |
| **AOF (Append Only File)** | Guarda cada operación en un log | Datos críticos |

#### En nuestro proyecto:

```yaml
command: redis-server --save 60 1 --loglevel warning
```

```
┌─────────────────────────────────────────────────────────────────┐
│                  EN NUESTRO PROYECTO                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ¿Qué guardamos en Redis?                                       │
│  ├── Ranking de vistas (se regenera solo)                      │
│  └── Sesiones de usuario (TTL de 1 hora)                        │
│                                                                 │
│  → Si Redis se cae y perdemos los datos:                      │
│     ├── El ranking vuelve a 0 (no importa)                     │
│     ├── Los usuarios deben loguearse de nuevo (tolerable)      │
│                                                                 │
│  → Por eso Redis NO necesita persistencia real en este caso    │
│  → El "--save 60 1" es solo por si acaso, no crítico          │
│                                                                 │
│  CONCLUSIÓN: Redisstore TODO en RAM para velocidad sub-        │
│  milisegundo. La persistencia (RDB) es opcional y nosotros     │
│  NO la usamos porque los datos son efímeros por diseño.       │
└─────────────────────────────────────────────────────────────────┘
```

**`volumes: mongo-data`**: Declara el volumen al final para que persista entre recreaciones del compose.

### 2.4 Cómo Levantarlo

```bash
# Levantar todos los servicios
docker-compose up -d

# Ver logs en tiempo real
docker-compose logs -f

# Detener todo
docker-compose down
```

El flag `-d` (detached mode) permite que los contenedores corran en background, liberando la terminal.

---

## 3. Spring Boot y Spring Data

### 3.1 ¿Qué es Spring Data?

Spring Data es un proyecto de Spring que **abstrae la capa de acceso a datos**, proporcionando una forma declarativa de interactuar con diferentes tipos de repositorios de datos. Su objetivo principal es reducir el código repetitivo necesario para las operaciones CRUD (Create, Read, Update, Delete).

### 3.2 Beneficios para el Proyecto

```
┌─────────────────────────────────────────────────────────────────┐
│                    ANTES (JDBC tradicional)                    │
├─────────────────────────────────────────────────────────────────┤
│  String query = "SELECT * FROM users WHERE username = ?";      │
│  PreparedStatement stmt = connection.prepareStatement(query); │
│  stmt.setString(1, username);                                  │
│  ResultSet rs = stmt.executeQuery();                           │
│  // Mapear manualmente cada fila a objeto                     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    DESPUÉS (Spring Data)                        │
├─────────────────────────────────────────────────────────────────┤
│  User user = userRepository.findByUsername(username);         │
│  // Listo. Spring genera la query automáticamente              │
└─────────────────────────────────────────────────────────────────┘
```

### 3.3 Dependencias del Proyecto (pom.xml)

```xml
<!-- Spring Boot Web -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

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

<!-- Seguridad JWT -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt</artifactId>
    <version>0.9.1</version>
</dependency>
```

### 3.4 Configuración de la Aplicación (application.yml)

```yaml
spring:
  data:
    mongodb:
      host: localhost
      port: 27017
      database: streaming_db
    redis:
      host: localhost
      port: 6379
```

Esta configuración es **declarativa**: no necesitamos escribir código de conexión, Spring Boot autoconfigura los beans necesarios basándose en las dependencias presentes.

### 3.4.1 ¿Qué es application.yml y qué hace?

`application.yml` es el **archivo central de configuración** de Spring Boot. Acá definimos todas las configuraciones sin escribir código Java.

```yaml
spring:
  data:
    mongodb:
      host: localhost
      port: 27017
      database: streaming_db
    redis:
      host: localhost
      port: 6379
```

#### Análisis línea por línea:

```
┌─────────────────────────────────────────────────────────────────┐
│                  APPLICATION.YML                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  spring:                    # Configuración de Spring           │
│    data:                   # Módulo Spring Data                  │
│      mongodb:              # Config de MongoDB                  │
│        host: localhost     # Dónde está la BD                  │
│        port: 27017         # Puerto de MongoDB                  │
│        database: streaming_db  # Nombre de la DB                │
│      redis:                # Config de Redis                    │
│        host: localhost     # Dónde está Redis                  │
│        port: 6379          # Puerto de Redis                   │
│                                                                 │
│  TOTAL: 6 líneas de config = conexión lista a ambas BDs        │
│  (Sin escribir una sola línea de código Java de conexión)      │
└─────────────────────────────────────────────────────────────────┘
```

#### ¿Qué hace Spring con esta configuración?

```
┌─────────────────────────────────────────────────────────────────┐
│            QUÉ GENERA SPRING CON ESTO                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Spring Boot hace "autoconfig":                                │
│                                                                 │
│  1. Ve que tenés spring-boot-starter-data-mongodb            │
│     → Crea MongoClient, MongoTemplate, etc.                   │
│                                                                 │
│  2. Ve spring.data.mongodb.*                                  │
│     → Configura la conexión:                                   │
│        - Conecta a localhost:27017                             │
│        - Usa la base "streaming_db"                           │
│                                                                 │
│  3. Lo mismo para Redis                                       │
│        - Crea RedisConnectionFactory                           │
│        - Crea RedisTemplate                                    │
│        - Conecta a localhost:6379                              │
│                                                                 │
│  4. Vos solo usás @Autowired y listo                         │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ EJEMPLO DE USO                                         │   │
│  │                                                         │   │
│  │ @Autowired                                             │   │
│  │ private MongoTemplate mongoTemplate;                  │   │
│  │                                                         │   │
│  │ // Spring ya lo-configuró, listo para usar            │   │
│  │ mongoTemplate.insert(contenido);                      │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

#### Comparación con otros frameworks:

```
┌─────────────────────────────────────────────────────────────────┐
│         ANTES vs DESPUÉS DE SPRING BOOT                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ANTES (Java EE tradicional):                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ // Vos tenías que escribir esto:                        │    │
│  │ MongoClient client = new MongoClient("localhost", 27017);│    │
│  │ MongoDatabase db = client.getDatabase("streaming_db");  │    │
│  │ MongoCollection<Document> coll = db.getCollection(...);│    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  DESPUÉS (Spring Boot):                                        │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ // application.yml = 6 líneas                          │    │
│  │ // Spring lo hace solo                                 │    │
│  │ // Vos usás @Autowired MongoTemplate mt;                │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  AHORRO: ~30 líneas de código de configuración                │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3.5 Los Beans en Spring Boot

### ¿Qué es un Bean?

Un **Bean** es un objeto que Spring Boot crea y gestiona automáticamente. Es el corazón de la **Inversión de Control (IoC)**.

### Sin Spring (cómo lo harías vos manualmente):

```java
// Vos-tenés que crear y conectar todo manualmente
public class Main {
    public static void main(String[] args) {
        // Crear el repository a mano
        UserRepository userRepo = new UserRepository();
        
        // Crear el service y pasarle el repository
        UserService userService = new UserService(userRepo);
        
        // Crear el controller y pasarle el service
        UserController userController = new UserController(userService);
        
        // Vos administrás el ciclo de vida de cada objeto
    }
}
```

### Con Spring (Spring lo hace por vos):

```java
// Solo definís qué necesitás, Spring lo crea y conecta
@Service
public class UserService {
    @Autowired  // Spring inyecta el bean automáticamente
    private UserRepository userRepository;
}

// Spring ve @Service y crea el bean automáticamente
// Cuando alguien necesite UserService, Spring se lo da
```

### ¿Cómo sabe Spring qué crear?

Las **anotaciones** le dicen a Spring qué role tiene cada clase:

```
┌─────────────────────────────────────────────────────────────────┐
│                    ANOTACIONES DE BEANS                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  @Component    → "Soy un componente genérico"                  │
│      ↓                                                         │
│  @Repository   → "Soy un repository (acceso a datos)"          │
│      ↓                                                         │
│  @Service      → "Soy un servicio (lógica de negocio)"         │
│      ↓                                                         │
│  @Controller   → "Soy un controlador web"                      │
│      ↓                                                         │
│  @Configuration → "Tengo configuración"                         │
│                                                                 │
│  Todas dicen: "CREÁME COMO BEAN"                              │
└─────────────────────────────────────────────────────────────────┘
```

### En nuestro proyecto:

```java
// Este es un BEAN de tipo SERVICE
@Service
public class StreamingService {
    // Este es un BEAN de tipo REPOSITORY (creado por Spring Data)
    @Autowired
    private ContenidoRepository contenidoRepository;
    
    // Este es un BEAN de tipo REDIS TEMPLATE ( autoconfigurado)
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
}
```

**Flujo de creación:**

```
┌─────────────────────────────────────────────────────────────────┐
│              CREACIÓN DE BEANS EN SPRING                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Tu código tiene @Autowired                                  │
│     → Spring ve que necesitás un StreamingService             │
│                                                                 │
│  2. Busca una clase con @Service que coincida                 │
│     → Encontró StreamingService                                 │
│                                                                 │
│  3. Crea UNA SOLA instancia (Singleton por defecto)           │
│     → La guarda en su "contenedor de beans"                    │
│                                                                 │
│  4. Cuando la necesités, Spring te la inyecta                  │
│     → Vos no llamás "new StreamingService()"                  │
│     → Vos solo declarás la dependencia y Spring te la da       │
│                                                                 │
│  BENEFICIO: Desacoplamiento total                              │
│  → El código no sabe quién crea los objetos                    │
│  → Fácil de tester (podes usar beans falsos/mocks)            │
└─────────────────────────────────────────────────────────────────┘
```

### Resumen de beans en nuestro proyecto:

| Bean | Tipo | Para qué |
|------|------|----------|
| `StreamingService` | @Service | Lógica de negocio |
| `ContenidoRepository` | @Repository | Acceso a MongoDB (creado por Spring Data) |
| `UserRepository` | @Repository | Acceso a MongoDB |
| `RedisTemplate<String, String>` | @Autowired | Operaciones con Redis |
| `StreamingController` | @Controller | Manejo de requests HTTP |

---

## 4. MongoDB: Persistencia de Datos

### 4.1 ¿Por Qué MongoDB?

MongoDB es una base de datos **documental** que almacena datos en formato BSON (Binary JSON). Es ideal para este proyecto porque:

1. **Modelo de datos flexible**: Los contenidos pueden tener diferentes metadatos sin alterar el esquema
2. **Relaciones embebidas**: El elenco y metadatos se almacenan junto con el documento
3. **Escalabilidad horizontal**: Easy sharding para grandes volúmenes
4. **Consultas enriquecidos**: Soporte para arrays, rangos, expresiones regulares

### 4.2 Modelo de Usuario

```java
@Document(collection = "users")
public class User {
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String username;
    
    @Indexed(unique = true)
    private String email;
    
    private String password;
    private String country;
    private String rol = "ROLE_USER";
}
```

**Análisis línea por línea:**

- **`@Document(collection = "users")`**: Indica que esta clase representa un documento en la colección "users" de MongoDB. Spring Data usará esto para mapear la clase a la colección.

- **`@Id`**: Marca el campo `id` como la clave primaria. MongoDB automáticamente genera un `ObjectId` si no se provee uno.

- **`@Indexed(unique = true)`**: Crea un índice único en los campos username y email. Esto garantiza que no puedan existir dos usuarios con el mismo username o email, funcionando como los constraints de las bases de datos relacionales.

**Equivalencia con SQL:**

| MongoDB | SQL equivalent |
|---------|---------------|
| `@Document` | CREATE TABLE |
| `@Id` | PRIMARY KEY |
| `@Indexed(unique = true)` | UNIQUE constraint |
| Campo `String` | VARCHAR |
| Campo `List<String>` | ARRAY/TEXT[] |

### 4.3 Modelo de Contenido (Diseño Embebido)

```java
@Document(collection = "contenidos")
public class Contenido {
    @Id
    private String id;
    private String titulo;
    private String tipo;
    private List<String> generos;      // Array embebido
    private String director;
    private Integer anioEstreno;
    private List<Actor> elenco;        // Relación 1-a-many embebida
    private Metadatos metadatos;       // Sub-documento 1-a-1

    // Clases embebidas dentro del documento padre
    public static class Actor {
        private String nombre;
        private String papel;
    }

    public static class Metadatos {
        private String calidad;
        private List<String> idiomas;
        private List<String> subtitulos;
    }
}
```

**Ventajas del diseño embebido:**

```
┌─────────────────────────────────────────────────────────────────┐
│              EN SQL (NORMALIZADO) - Múltiples tablas          │
├─────────────────────────────────────────────────────────────────┤
│  Tabla contenidos                                               │
│  ├── id, titulo, director, año                                 │
│                                                              │
│  Tabla actores (relación many-to-many con join table)        │
│  ├── contenido_id, actor_id                                   │
│                                                              │
│  Tabla actores                                                │
│  ├── id, nombre                                               │
│                                                              │
│  Tabla metadatos (relación 1-a-1)                             │
│  ├── contenido_id, calidad, idiomas                           │
└─────────────────────────────────────────────────────────────────┘

Para obtener UNA película con todo su elenco y metadatos:
→ 1 query para contenidos
→ 1 JOIN con actores
→ 1 JOIN con actores_nombres
→ 1 JOIN con metadatos
= 4 operaciones de base de datos
```

```
┌─────────────────────────────────────────────────────────────────┐
│              EN MONGODB (EMBEDIDO) - Un solo documento         │
├─────────────────────────────────────────────────────────────────┤
│  {                                                             │
│    "_id": "obj123",                                            │
│    "titulo": "Inception",                                      │
│    "director": "Christopher Nolan",                            │
│    "anioEstreno": 2010,                                        │
│    "elenco": [                                                 │
│      { "nombre": "Leonardo DiCaprio", "papel": "Cobb" },       │
│      { "nombre": "Joseph Gordon-Levitt", "papel": "Arthur" }  │
│    ],                                                          │
│    "metadatos": {                                              │
│      "calidad": "4K",                                          │
│      "idiomas": ["Inglés", "Español"]                         │
│    }                                                           │
│  }                                                             │
└─────────────────────────────────────────────────────────────────┘

Para obtener UNA película:
→ 1 operación de base de datos
→ Documento completo con relaciones embebidas
```

### 4.4 Repositorios MongoDB

#### UserRepository

```java
@Repository
public interface UserRepository extends MongoRepository<User, String> {
    // Spring Data genera automáticamente la query basada en el nombre del método
    Optional<User> findByUsername(String username);
    
    boolean existsByEmail(String email);
    
    Optional<User> findByEmail(String email);
}
```

**¿Cómo funciona?**

Cuando declaramos `findByUsername(String username)`, Spring Data:
1. Analiza el nombre del método: `find` + `By` + `Username`
2. Genera internamente: `db.users.findOne({ username: "valorRecibido" })`
3. Ejecuta la query contra MongoDB
4. Mapea el resultado a un objeto `Optional<User>`

Este patrón se llama **Query Methods** y elimina la necesidad de escribir consultas manualmente.

#### ContenidoRepository

```java
@Repository
public interface ContenidoRepository extends MongoRepository<Contenido, String> {
    // Búsqueda por contenido de array
    List<Contenido> findByGenerosContaining(String genero);
}
```

**¿Qué hace `findByGenerosContaining`?**

Para una llamada como `findByGenerosContaining("Acción")`, Spring Data genera:
```javascript
db.contenidos.find({ generos: { $in: ["Acción"] } })
```

MongoDB Busca documentos donde el array `generos` contenga "Acción".

---

## 5. Redis: Operaciones de Alta Velocidad

### 5.1 ¿Por Qué Redis?

Redis es un almacén de datos **en memoria** que ofrece tiempos de acceso sub-milisegundos. Lo usamos para:

1. **Sesiones con TTL**: Expiración automática sin código adicional
2. **Contadores atómicos**: Incrementos concurrentes sin race conditions
3. **Rankings en tiempo real**: Sorted Sets para top de vistas
4. **Cache**: Reducir carga en MongoDB

### 5.2 Modelo de Sesión con TTL

```java
@RedisHash("user:session")
public class UserSession {
    @Id
    private String id;
    
    @Indexed
    private String userId;
    
    private String lastContentId;
    private Long timestamp;

    @TimeToLive
    private Long timeToLive;  // TTL automático
}
```

**Análisis crítico:**

- **`@RedisHash("user:session")`**: Define el namespace para las claves de Redis. Spring Data Redis crea claves con prefijo `user:session:`.

- **`@TimeToLive`**: Esta es la **magia de Redis**. Cuando se guarda un objeto con esta anotación, Redis automáticamente:
  1. Guarda el documento con un tiempo de expiración
  2. Elimina el documento cuando expira
  3. No requiere código de limpieza manual

- **Caso de uso**: Sesiones de usuario que deben expirar después de 1 hora. En una base de datos tradicional, necesitaríamos un job scheduler que limpie sesiones vencidas. Con Redis y `@TimeToLive`, es automático.

### 5.3 Repositorio de Sesiones (Operaciones con Hash)

```java
@Repository
public class UserSessionRepository {
    private final RedisTemplate<String, String> redisTemplate;
    
    private static final String KEY_PREFIX = "user:session:";
    private static final String FIELD_LAST_CONTENT = "lastContentId";
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final long ttl = 3600L; // 1 hora

    public void save(UserSession session) {
        // Construir la clave: "user:session:user123"
        String key = KEY_PREFIX + session.getUserId();
        
        // Crear el mapa de campos
        Map<String, String> hashMap = new HashMap<>();
        hashMap.put(FIELD_LAST_CONTENT, session.getLastContentId());
        hashMap.put(FIELD_TIMESTAMP, String.valueOf(session.getTimestamp()));
        
        // HSET + EXPIRE en una operación atómica
        redisTemplate.opsForHash().putAll(key, hashMap);
        redisTemplate.expire(key, ttl, TimeUnit.SECONDS);
    }
    
    public Optional<UserSession> findByUserId(String userId) {
        String key = KEY_PREFIX + userId;
        
        // HGETALL - obtener todos los campos del hash
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        
        // Reconstruir el objeto desde el hash
        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setLastContentId((String) entries.get(FIELD_LAST_CONTENT));
        session.setTimestamp(Long.parseLong((String) entries.get(FIELD_TIMESTAMP)));
        
        return Optional.of(session);
    }
}
```

**Comandos Redis que se ejecutan:**

| Método Spring | Comando Redis | Descripción |
|---------------|---------------|-------------|
| `opsForHash().putAll()` | `HSET user:session:user123 lastContentId "x" timestamp "y"` | Guarda campos en el hash |
| `expire()` | `EXPIRE user:session:user123 3600` | Establece TTL de 1 hora |
| `opsForHash().entries()` | `HGETALL user:session:user123` | Obtiene todos los campos |

### 5.4 Sorted Set para Rankings

```java
@Service
public class StreamingService {
    private static final String RANKING_KEY = "ranking:vistas:global";
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    private ZSetOperations<String, String> getZSetOperations() {
        return redisTemplate.opsForZSet();
    }
    
    // Incrementar vistas - O(log N)
    public void incrementVistas(String contenidoId) {
        // ZINCRBY ranking:vistas:global 1 "contenido123"
        getZSetOperations().incrementScore(RANKING_KEY, contenidoId, 1);
    }
    
    // Obtener top 5 - O(log N + 5)
    public Set<ZSetOperations.TypedTuple<String>> getTop5Vistas() {
        // ZREVRANGE ranking:vistas:global 0 4 WITHSCORES
        return getZSetOperations().reverseRangeWithScores(RANKING_KEY, 0, 4);
    }
}
```

**Por qué Sorted Set es ideal para rankings:**

```
Situación: Mil usuarios ven contenido simultáneamente

┌─────────────────────────────────────────────────────────────────┐
│  SIN Sorted Set (enfoque con tabla SQL)                        │
├─────────────────────────────────────────────────────────────────┤
│  1. Leer count actual de la base de datos                     │
│  2. Incrementar en memoria                                     │
│  3. Escribir de vuelta                                         │
│                                                                 │
│  PROBLEMA: Race condition!                                     │
│  ├── Usuario A lee: vistas = 100                              │
│  ├── Usuario B lee: vistas = 100                              │
│  ├── Usuario A escribe: vistas = 101                         │
│  ├── Usuario B escribe: vistas = 101  ← DEBERÍA SER 102      │
│  └── Resultado: 101 vistas (se perdió 1 incremento)          │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  CON Sorted Set (ZINCRBY - atómico)                            │
├─────────────────────────────────────────────────────────────────┤
│  Redis ejecuta: ZINCRBY ranking:vistas:global 1 "contenido1" │
│                                                                 │
│  operación atómica - no hay race condition                     │
│  Redis maneja la concurrencia internamente                     │
│  ──────────────────────────────────────────────────────────   │
│  Complejidad: O(log N) donde N = cantidad de contenidos       │
│  (N=1000 contenidos → ~10 operaciones por incremento)         │
└─────────────────────────────────────────────────────────────────┘
```

---

## 6. Servicios: Coordinación de Bases de Datos

### 6.1 StreamingService

```java
@Service
public class StreamingService {
    
    @Autowired
    private ContenidoRepository contenidoRepository;
    
    @Autowired
    private UserSessionRepository userSessionRepository;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    private static final String RANKING_KEY = "ranking:vistas:global";
    
    // ============ OPERACIONES MONGODB ============
    
    public Contenido insertarContenido(Contenido contenido) {
        return contenidoRepository.save(contenido);
    }
    
    public List<Contenido> listarContenidos() {
        return contenidoRepository.findAll();
    }
    
    public List<Contenido> buscarPorGenero(String genero) {
        return contenidoRepository.findByGenerosContaining(genero);
    }
    
    // ============ OPERACIONES REDIS - VISTAS ============
    
    public void incrementVistas(String contenidoId) {
        redisTemplate.opsForZSet().incrementScore(RANKING_KEY, contenidoId, 1);
    }
    
    public List<Map<String, Object>> getTop5Vistas() {
        Set<ZSetOperations.TypedTuple<String>> top5 = 
            redisTemplate.opsForZSet().reverseRangeWithScores(RANKING_KEY, 0, 4);
        
        List<Map<String, Object>> result = new ArrayList<>();
        if (top5 != null) {
            for (ZSetOperations.TypedTuple<String> entry : top5) {
                Map<String, Object> item = new HashMap<>();
                item.put("contenidoId", entry.getValue());
                item.put("vistas", entry.getScore().longValue());
                result.add(item);
            }
        }
        return result;
    }
    
    // ============ OPERACIONES REDIS - SESIONES ============
    
    public void guardarSesion(String userId, String contenidoId) {
        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setLastContentId(contenidoId);
        session.setTimestamp(System.currentTimeMillis());
        session.setTimeToLive(3600L); // 1 hora
        
        userSessionRepository.save(session);
    }
    
    public Optional<UserSession> obtenerSesion(String userId) {
        return userSessionRepository.findByUserId(userId);
    }
}
```

**Patrón Service Layer:**

El servicio actúa como **orquestador** entre las diferentes bases de datos:
- MongoDB para el catálogo de contenidos (persistente)
- Redis para las métricas de vistas (efímero) y sesiones (TTL)

---

## 7. Controladores REST

### 7.1 StreamingController

```java
@RestController
@RequestMapping("/api")
public class StreamingController {
    
    @Autowired
    private StreamingService streamingService;
    
    // ============ ENDPOINTS MONGODB ============
    
    @PostMapping("/contenidos")
    public ResponseEntity<Contenido> insertarContenido(@RequestBody Contenido contenido) {
        Contenido guardado = streamingService.insertarContenido(contenido);
        return ResponseEntity.ok(guardado);
    }
    
    @GetMapping("/contenidos")
    public ResponseEntity<List<Contenido>> listarContenidos() {
        List<Contenido> contenidos = streamingService.listarContenidos();
        return ResponseEntity.ok(contenidos);
    }
    
    @GetMapping("/contenidos/genero/{genero}")
    public ResponseEntity<List<Contenido>> buscarPorGenero(@PathVariable String genero) {
        List<Contenido> contenidos = streamingService.buscarPorGenero(genero);
        return ResponseEntity.ok(contenidos);
    }
    
    // ============ ENDPOINTS REDIS - VISTAS ============
    
    @PostMapping("/vistas/{contenidoId}")
    public ResponseEntity<Void> incrementVistas(@PathVariable String contenidoId) {
        streamingService.incrementVistas(contenidoId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/ranking/top5")
    public ResponseEntity<List<Map<String, Object>>> getTop5Vistas() {
        List<Map<String, Object>> ranking = streamingService.getTop5Vistas();
        return ResponseEntity.ok(ranking);
    }
    
    // ============ ENDPOINTS REDIS - SESIONES ============
    
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
```

**Anotaciones críticas:**

- **`@RestController`**: Combina `@Controller` y `@ResponseBody`. Indica que esta clase maneja requests HTTP y retorna objetos que se serializan directamente a JSON.

- **`@RequestMapping("/api")`**: Define el path base para todos los endpoints de este controlador.

- **`@PostMapping` / `@GetMapping` / `@PathVariable` / `@RequestParam`**: Mapean URLs a métodos y extraen parámetros de la request.

---

## 8. Patrones de Diseño Utilizados

### 8.1 Repository Pattern

```
┌─────────────────────────────────────────────────────────────────┐
│                     ABSTRACCIÓN DEL REPOSITORY                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  CONTROLADOR → SERVICIO → REPOSITORIO → MONGODB/REDIS         │
│       │              │              │            │              │
│       ▼              ▼              ▼            ▼              │
│  No sabe qué      No sabe      Abstrae la    Implementación    │
│  base de datos   base de      complejidad    específica         │
│  usa             datos usa    del acceso      de la BD          │
│                                                                 │
│  BENEFICIO: Si mañana migramos de MongoDB a Cassandra,         │
│  solo cambiamos los Repository, el resto del código            │
│  permanece igual                                                │
└─────────────────────────────────────────────────────────────────┘
```

### 8.2 Service Layer

Separa la lógica de negocio de los controladores. El servicio:
- Coordina múltiples repositorios (MongoDB + Redis)
- Contiene la lógica de negocio
- Es testeable independientemente de los controladores

### 8.3 Polyglot Persistence

```
┌─────────────────────────────────────────────────────────────────┐
│              POLYGLOT PERSISTENCE EN EL PROYECTO                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────┐     ┌─────────────────────┐           │
│  │       MONGODB       │     │        REDIS        │           │
│  │                     │     │                     │           │
│  │  📊 Datos           │     │  ⚡ Datos            │           │
│  │  persistentes       │     │  efímeros           │           │
│  │                     │     │                     │           │
│  │  - Usuarios         │     │  - Vistas (contador)│           │
│  │  - Contenidos       │     │  - Sesiones (TTL)   │           │
│  │  - Catálogo        │     │  - Ranking (sorted) │           │
│  │                     │     │                     │           │
│  │ 读写频繁            │     │  超快读写            │           │
│  │ (datos importantes) │     │  (datos temporales) │           │
│  └─────────────────────┘     └─────────────────────┘           │
│                                                                 │
│  DECISIÓN: "La herramienta correcta para el trabajo correcto" │
└─────────────────────────────────────────────────────────────────┘
```

---

## 9. Integración Frontend

### 9.1 API del Frontend (JavaScript)

```javascript
// src/main/resources/static/js/api.js

export const api = {
    // ============ MONGODB ============
    
    // Obtener todos los contenidos
    async fetchCatalog() {
        const res = await fetch('/api/contenidos');
        return res.json();
    },
    
    // Buscar por género
    async fetchGenre(genre) {
        const res = await fetch(`/api/contenidos/genero/${genre}`);
        return res.json();
    },
    
    // ============ REDIS - VISTAS ============
    
    // Registrar vista (incrementa contador)
    async view(id) {
        await fetch(`/api/vistas/${id}`, { method: 'POST' });
    },
    
    // Obtener top 5
    async ranking() {
        const res = await fetch('/api/ranking/top5');
        return res.json();
    },
    
    // ============ REDIS - SESIONES ============
    
    // Guardar sesión
    async saveSession(userId, contentId) {
        await fetch(`/api/sesion/${userId}?contenidoId=${contentId}`, { 
            method: 'POST' 
        });
    },
    
    // Obtener sesión activa
    async getSession(userId) {
        const res = await fetch(`/api/sesion/${userId}`);
        return res.json();
    }
};
```

### 9.2 Flujo de Integración

```
┌─────────────────────────────────────────────────────────────────┐
│                    FLUJO FRONTEND → BACKEND                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Usuario hace click en "Ver película"                       │
│     → app.js llama a api.view(contenidoId)                    │
│                                                                 │
│  2. fetch('/api/vistas/' + contenidoId, {method: 'POST'})    │
│     → Request HTTP al servidor Spring                         │
│                                                                 │
│  3. StreamingController.incrementVistas()                    │
│     → StreamingService.incrementVistas()                      │
│     → Redis ZINCRBY (contador atómico)                        │
│                                                                 │
│  4. Respuesta HTTP 200 OK                                     │
│     → Frontend actualiza UI                                   │
│                                                                 │
│  ¿Por qué Redis para esto?                                     │
│  - Cada vista es una operación rápida                         │
│  - No necesitamos persistencia duradera                       │
│  - El ranking cambia constantemente                           │
│  - MongoDB sería overkill para contadores                     │
└─────────────────────────────────────────────────────────────────┘
```

---

## 10. Ventajas y Desventajas de NoSQL

### 10.1 Ventajas de MongoDB

| Ventaja | Explicación | Ejemplo en el Proyecto |
|---------|-------------|------------------------|
| **Esquema flexible** | Los documentos pueden tener campos diferentes | Cada contenido puede tener diferentes metadatos |
| **Consultas enriched** | Soporte para arrays, objetos anidados | `findByGenerosContaining` busca dentro de arrays |
| **Alta disponibilidad** | Replica sets automáticos | No implementado pero soportado |
| **Escalabilidad horizontal** | Sharding nativo | Para millones de contenidos |
| **Desarrollo rápido** | Sin migraciones de esquema | Agregar campo nuevo = solo código |

### 10.2 Desventajas de MongoDB

| Desventaja | Explicación | Mitigación en el Proyecto |
|------------|-------------|---------------------------|
| **Sin transacciones ACID** | No garantiza atomicidad entre operaciones | Diseñar operaciones como documentos atómicos |
| **Consistencia eventual** | En réplicas, los datos pueden no estar sincronizados | Usar write concern acknowledged |
| **Consumo de memoria** | Mantiene datos en RAM para velocidad | Limitar indices, implementar TTL |
| **Curva de aprendizaje** | Queries diferentes a SQL | Spring Data abstrae la complejidad |

### 10.3 Ventajas de Redis

| Ventaja | Explicación | Ejemplo en el Proyecto |
|---------|-------------|------------------------|
| **Velocidad sub-milisegundo** | Datos en memoria | Sesiones y rankings instantáneos |
| **Estructuras de datos avanzadas** | Lists, Sets, Sorted Sets, Hashes | Sorted Set para ranking con ZINCRBY |
| **TTL automático** | Expiración de claves sin código | Sesiones que expiran en 1 hora |
| **Operaciones atómicas** | Sin race conditions | Incremento de vistas concurrently |
| **Pub/Sub integrado** | Messaging incorporado | No usado pero disponible |

### 10.4 Desventajas de Redis

| Desventaja | Explicación | Mitigación en el Proyecto |
|------------|-------------|---------------------------|
| **Datos efímeros por diseño** | Si falla el proceso, se pierde todo | MongoDB es la fuente de verdad |
| **Memoria limitada** | Todo en RAM, costoso para grandes volúmenes | Solo datos efímeros en Redis |
| **Persistencia opcional** | RDB/AOF pueden perder datos | No es crítica la pérdida de sesiones |
| **Single-threaded** | Opera una comando a la vez | Suficiente para nuestro volumen |

---

## 11. Flujo de Datos Completo

### 11.1 Diagrama de Arquitectura

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              USUARIO (BROWSER)                              │
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────┐      │
│   │                      FRONTEND (HTML/JS)                         │      │
│   │                                                                 │      │
│   │   index.html                                                    │      │
│   │      ├── app.js (lógica principal)                             │      │
│   │      ├── api.js (llamadas HTTP al backend)                    │      │
│   │      ├── state.js (gestión de estado)                         │      │
│   │      └── ui.js (renderizado de interfaz)                      │      │
│   └────────────────────────────┬────────────────────────────────────┘      │
│                                │ HTTP JSON                                  │
└────────────────────────────────┼────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SPRING BOOT (BACKEND)                               │
│                                                                             │
│   ┌─────────────────────────────────────────────────────────────────────┐  │
│   │                     @RestController Layer                         │  │
│   │                                                                      │  │
│   │   @PostMapping("/contenidos")    → guardar contenido en MongoDB   │  │
│   │   @GetMapping("/contenidos")     → listar desde MongoDB           │  │
│   │   @PostMapping("/vistas/{id}")   → incrementar en Redis          │  │
│   │   @GetMapping("/ranking/top5")    → leer desde Redis              │  │
│   │   @PostMapping("/sesion/{id}")   → guardar sesión en Redis       │  │
│   │   @GetMapping("/sesion/{id}")    → leer sesión desde Redis       │  │
│   └─────────────────────────────┬───────────────────────────────────────┘  │
│                                   │                                          │
│   ┌──────────────────────────────▼───────────────────────────────────────┐  │
│   │                       @Service Layer                               │  │
│   │                                                                        │  │
│   │   StreamingService                                                    │  │
│   │   ├── ContenidoRepository → MongoDB                                 │  │
│   │   ├── UserSessionRepository → Redis                                 │  │
│   │   └── RedisTemplate → operaciones directas                          │  │
│   └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────┬───────────────────────────────────────────────┘
                                 │
          ┌──────────────────────┼──────────────────────┐
          │                      │                      │
          ▼                      ▼                      ▼
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│     MONGODB     │   │      REDIS      │   │      DOCKER     │
│                 │   │                 │   │                 │
│ Colección:      │   │ Keys:           │   │ Contenedor 1:   │
│ - users         │   │ - ranking:*     │   │ spring-app      │
│ - contenidos    │   │ - user:session:*│   │                 │
│                 │   │                 │   │ Contenedor 2:   │
│ Documentos:     │   │ Estructuras:    │   │ mongodb         │
│ - usuarios      │   │ - Sorted Set    │   │                 │
│ - películas     │   │ - Hash          │   │ Contenedor 3:   │
│ - series        │   │                 │   │ redis           │
└─────────────────┘   └─────────────────┘   └─────────────────┘
```

### 11.2 Flujo 1: Registro de Usuario

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    FLUJO: REGISTRO DE USUARIO                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  FRONTEND                                                                  │
│  ┌─────────────────┐                                                       │
│  │ POST /api/auth  │ { username: "juan", email: "...", password: "..." } │
│  └────────┬────────┘                                                       │
│           │ HTTP Request                                                   │
└───────────┼───────────────────────────────────────────────────────────────┘
            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  BACKEND                                                                   │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ AuthController.register()                                          │   │
│  │     ↓                                                               │   │
│  │ UserService.registrarUsuario(user)                                 │   │
│  │     ↓                                                               │   │
│  │ userRepository.save(user)                                          │   │
│  │     ↓                                                               │   │
│  │ Spring Data genera: db.users.insertOne({...})                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│           │                                                               │
└───────────┼───────────────────────────────────────────────────────────────┘
            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  MONGODB                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ {                                                                     │   │
│  │   "_id": "ObjectId('...')",                                         │   │
│  │   "username": "juan",                                               │   │
│  │   "email": "juan@email.com",                                        │   │
│  │   "password": "hash...",  // BCrypt encriptado                      │   │
│  │   "country": "Argentina",                                           │   │
│  │   "rol": "ROLE_USER"                                                │   │
│  │ }                                                                    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ✅ Usuario persistido permanentemente                                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 11.3 Flujo 2: Ver Contenido (Incrementar Ranking)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    FLUJO: VER CONTENIDO                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  FRONTEND                                                                  │
│  ┌──────────────────────────┐                                            │
│  │ POST /api/vistas/abc123  │ (usuario hace click en película)          │
│  └────────────┬─────────────┘                                            │
│               │ HTTP Request                                              │
└───────────────┼───────────────────────────────────────────────────────────┘
                ▼
┌───────────────────────────────────────────────────────────────────────────┐
│  BACKEND                                                                   │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ StreamingController.incrementVistas("abc123")                     │  │
│  │     ↓                                                              │  │
│  │ streamingService.incrementVistas("abc123")                        │  │
│  │     ↓                                                              │  │
│  │ redisTemplate.opsForZSet().incrementScore("ranking:vistas:...",  │  │
│  │                                             "abc123", 1)          │  │
│  │     ↓                                                              │  │
│  │ COMANDO: ZINCRBY ranking:vistas:global 1 "abc123"                 │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│               │                                                           │
└───────────────┼───────────────────────────────────────────────────────────┘
                ▼
┌───────────────────────────────────────────────────────────────────────────┐
│  REDIS                                                                      │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ KEY: "ranking:vistas:global"  (Sorted Set)                        │  │
│  │                                                             score  │  │
│  │  "abc123" ───────────────────────────────────────────────→   156  │  │
│  │  "def456" ───────────────────────────────────────────────→   142  │  │
│  │  "ghi789" ───────────────────────────────────────────────→    98  │  │
│  │   ...                                                             │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ✅ Contador incrementado atómicamente                                     │
│  ✅ Datos efímeros - si Redis se reinicia, el ranking se reinicia         │
│  ✅ O(log N) complejidad - muy rápido incluso con millones de contenidos│
└───────────────────────────────────────────────────────────────────────────┘
```

### 11.4 Flujo 3: Obtener Top 5

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    FLUJO: OBTENER TOP 5 VISTAS                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  FRONTEND                                                                  │
│  ┌──────────────────────────┐                                            │
│  │ GET /api/ranking/top5    │ (frontend pide ranking)                    │
│  └────────────┬─────────────┘                                            │
│               │ HTTP Request                                              │
└───────────────┼───────────────────────────────────────────────────────────┘
                ▼
┌───────────────────────────────────────────────────────────────────────────┐
│  BACKEND                                                                   │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ streamingService.getTop5Vistas()                                  │  │
│  │     ↓                                                              │  │
│  │ redisTemplate.opsForZSet()                                         │  │
│  │    .reverseRangeWithScores("ranking:vistas:global", 0, 4)         │  │
│  │     ↓                                                              │  │
│  │ COMANDO: ZREVRANGE ranking:vistas:global 0 4 WITHSCORES          │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│               │                                                           │
└───────────────┼───────────────────────────────────────────────────────────┘
                ▼
┌───────────────────────────────────────────────────────────────────────────┐
│  REDIS                                                                      │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ ZREVRANGE retorna:                                                 │  │
│  │                                                                     │  │
│  │ [                                                                  │  │
│  │   { value: "abc123", score: 156 },  // 1er lugar                  │  │
│  │   { value: "def456", score: 142 },  // 2do lugar                  │  │
│  │   { value: "ghi789", score: 98 },   // 3er lugar                  │  │
│  │   { value: "jkl012", score: 87 },   // 4to lugar                  │  │
│  │   { value: "mno345", score: 76 }    // 5to lugar                  │  │
│  │ ]                                                                  │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│               │                                                           │
└───────────────┼───────────────────────────────────────────────────────────┘
                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  FRONTEND (respuesta JSON)                                                 │
│  [                                                                          │
│    { "contenidoId": "abc123", "vistas": 156 },                              │
│    { "contenidoId": "def456", "vistas": 142 },                              │
│    { "contenidoId": "ghi789", "vistas": 98 },                               │
│    { "contenidoId": "jkl012", "vistas": 87 },                               │
│    { "contenidoId": "mno345", "vistas": 76 }                                │
│  ]                                                                          │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 11.5 Flujo 4: Guardar Sesión de Usuario

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    FLUJO: GUARDAR SESIÓN                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  FRONTEND                                                                  │
│  ┌──────────────────────────────────────────────┐                        │
│  │ POST /api/sesion/user123?contenidoId=abc123  │                        │
│  └────────────────────┬─────────────────────────┘                        │
│                       │ HTTP Request                                      │
└───────────────────────┼────────────────────────────────────────────────────┘
                       ▼
┌───────────────────────────────────────────────────────────────────────────┐
│  BACKEND                                                                   │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ StreamingController.guardarSesion("user123", "abc123")            │  │
│  │     ↓                                                              │  │
│  │ streamingService.guardarSesion("user123", "abc123")               │  │
│  │     ↓                                                              │  │
│  │ UserSession session = new UserSession()                           │  │
│  │ session.setUserId("user123")                                      │  │
│  │ session.setLastContentId("abc123")                                │  │
│  │ session.setTimestamp(System.currentTimeMillis())                 │  │
│  │ session.setTimeToLive(3600L)    // 1 hora                          │  │
│  │     ↓                                                              │  │
│  │ userSessionRepository.save(session)                               │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│               │                                                           │
└───────────────┼───────────────────────────────────────────────────────────┘
                ▼
┌───────────────────────────────────────────────────────────────────────────┐
│  REDIS                                                                      │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ COMANDOS:                                                          │  │
│  │                                                                     │  │
│  │ 1. HSET user:session:user123 lastContentId "abc123"               │  │
│  │ 2. HSET user:session:user123 timestamp "1751821800000"             │  │
│  │ 3. EXPIRE user:session:user123 3600                                │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  CLAVE: "user:session:user123"                                             │
│  VALOR (Hash): { "lastContentId": "abc123", "timestamp": "1751821800000"}│
│  TTL: 3600 segundos (1 hora)                                              │
│                                                                             │
│  ✅ Sesión expira automáticamente después de 1 hora                      │
│  ✅ No requiere código de limpieza manual                                │
└───────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Resumen Ejecutivo

### 12.1 ¿Por Qué Esta Arquitectura?

| Decisión | Justificación |
|----------|---------------|
| **MongoDB para datos persistentes** | Catálogo de usuarios y contenidos necesita esquema flexible y consultas enriched |
| **Redis para operaciones rápidas** | Contadores, rankings y sesiones son datos efímeros que requieren velocidad |
| **Spring Data** | Abstrae la complejidad de ambas bases de datos, permitiendo código declarativo |
| **Docker** | Infraestructura consistente y reproducible |

### 12.2 Conclusiones Clave

1. **Polyglot Persistence**: Usar la herramienta correcta para cada caso. MongoDB no es mejor que Redis ni viceversa; son diferentes y se complementan.

2. **Diseño Embebido vs. Normalización**: MongoDB permite guardar relaciones dentro del documento, eliminando la necesidad de JOINs.

3. **TTL Automático**: Redis maneja la expiración de datos automáticamente, simplificando la gestión de sesiones.

4. **Operaciones Atómicas**: Redis garantiza consistencia en operaciones concurrentes gracias a su modelo single-threaded y comandos atómicos.

5. **Spring Data**: La abstracción de repositorios permite cambiar de base de datos con mínimo impacto en el código de negocio.

---

*Documento preparado para presentación de Bases de Datos No Relacionales*