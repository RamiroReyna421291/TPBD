# Streaming App - Sistema de Gestión de Contenidos

## 📋 Descripción del Proyecto

API RESTful para sistema de streaming realizada como trabajo práctico de bases de datos. El objetivo es practicar y comparar el uso de bases de datos **NoSQL** (MongoDB y Redis) versus bases de datos tradicionales **SQL**.

Este proyecto demuestra un escenario real donde diferentes tipos de bases de datos cumplen funciones específicas según sus fortalezas.

---

## 🏗️ Arquitectura del Sistema

```
┌─────────────┐     ┌─────────────┐
│   Frontend  │────▶│  Spring Boot│
│  (HTML/JS)  │     │    App      │
└─────────────┘     └──────┬──────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│    MongoDB    │  │     Redis     │  │    Tomcat     │
│ (Persistencia │  │ (Ranking +    │  │  (Puerto      │
│  de Contenido)│  │  Sesiones)    │  │   8080)       │
└───────────────┘  └───────────────┘  └───────────────┘
```

### ¿Por qué esta distribución?

- **MongoDB**: Almacena el catálogo de contenidos (datos complejos y variables)
- **Redis**: Maneja contadores de alta velocidad (rankings) y datos efímeros (sesiones)
- **Spring Boot**: Coordina ambas bases de datos y expone la API REST

---

## 🛠️ Tecnologías Utilizadas

| Componente | Tecnología | Versión |
|------------|------------|---------|
| Backend | Spring Boot | 3.2.0 |
| Base de datos principal | MongoDB | Latest |
| Cache/Ranking | Redis | Latest |
| Build | Maven | 3.x |
| Java | JDK | 17+ |

---

## 📁 Estructura del Proyecto

```
src/main/java/com/streaming/
├── StreamingApplication.java      # Clase principal
├── config/
│   └── RedisConfig.java          # Configuración de Redis (ZSetOperations, Hash)
├── controller/
│   └── StreamingController.java  # Endpoints REST
├── model/
│   ├── Contenido.java            # Documento MongoDB
│   └── UserSession.java          # Entidad Redis Hash
├── repository/
│   └── ContenidoRepository.java  # Repository MongoDB (MongoRepository)
└── service/
    └── StreamingService.java     # Lógica de negocio

src/main/resources/
├── application.yml               # Configuración de conexiones
└── static/
    └── index.html                # Frontend de prueba
```

---

## 📊 Decisiones de Diseño Explicadas

### MongoDB: ¿Por qué usarlo para Contenidos?

El modelo `Contenido` utiliza **documentos embebidos** (embedded documents) para representar datos relacionados:

```java
// Actor y Metadatos son clases internas embebidas en Contenido
@Document(collection = "contenidos")
public class Contenido {
    private List<Actor> elenco;      // Documentos embebidos
    private Metadatos metadatos;     // Documento embebido
}
```

#### Ventajas de esta aproximación:

1. **Sin JOINS**: Los actores y metadatos siempre acompañan al contenido
2. **Flexible Schema**: Podés agregar campos nuevos sin migraciones
3. **Lectura única**: Un query trae toda la información relacionada

#### Desventajas a considerar:

1. **Documentos grandes**: Si un contenido tiene 1000 actores, el documento crece mucho
2. **Actualización costosa**: Modificar un actor requiere reescribir el documento
3. **Limitación de índices**: No podés indexar campos dentro de arrays embebidos eficientemente

#### Cuándo sería mejor usar SQL para esto:

- Si necesitás normalizar drastically (evitar redundancia)
- Si tenés millones de actores compartidos entre películas
- Si necesitás queries complejos con múltiples JOINs

---

### Redis: ¿Por qué usarlo para Sesiones y Ranking?

#### Sesiones de Usuario (Hash + TTL)

```java
// En Redis se guarda como:
// Key: "user:user1:session"
// Hash: { lastContentId: "con123", timestamp: "1751821800" }
// TTL: 3600 segundos (1 hora)
```

**Ventajas:**
- ✅ Expiración automática: no necesitás limpiar sesiones manualmente
- ✅ Velocidad en memoria: acceso en microsegundos
- ✅ Estructura simple: clave-valor con campos definidos

**Desventajas:**
- ❌ Persistencia limitada: si Redis falla, perdés sesiones activas
- ❌ No es ACID: puede haber inconsistencias temporales
- ❌ Memoria finita: limitado por RAM disponible

#### Ranking de Vistas (Sorted Set)

```java
// En Redis se guarda como:
// Key: "ranking:vistas:global"
// Sorted Set: { contenido1: 1500, contenido2: 1200, ... }
```

**Ventajas:**
- ✅ `ZINCRBY`: operación atómica para incrementar contadores concurrentes
- ✅ `ZRANGE REV`: top N en O(log N + N) - extremadamente rápido
- ✅ No necesitás mantener una tabla SQL con índices complejos

**Desventajas:**
- ❌ Solo guarda ID y score, no metadata adicional
- ❌ Persistencia: si reiniciás Redis, perdés el ranking
- ❌ Solo funciona bien para el caso de uso específico

---

## 🔄 Comparativa: SQL vs NoSQL

### Diferencias Fundamentales

| Aspecto | SQL (PostgreSQL, MySQL) | NoSQL (MongoDB, Redis) |
|---------|-------------------------|--------------------------|
| **Modelo de datos** | Tablas con filas y columnas | Documentos, claves-valor, grafos |
| **Schema** | Fijo (requiere migraciones) | Flexible (schemaless) |
| **Relaciones** | JOINs nativos | No existen (embedding o referencias) |
| **Transacciones** | ACID completo | Eventually consistent |
| **Escalabilidad** | Vertical (mejor hardware) | Horizontal (sharding) |
| **Lenguaje de queries** | SQL universal | Proprietario por base de datos |
| **Índices** | Soporte complejo y potente | Básico en MongoDB, limitado en Redis |

### Cuándo usar SQL

- **Datos estructurados con relaciones claras**: clientes → pedidos → productos
- **Transacciones complejas**: sistemas bancarios, inventarios
- **Queries analíticos complejos**: reportes, agregaciones
- **Integridad de datos crítica**: constraints, foreign keys

### Cuándo usar MongoDB

- **Datos semi-estructurados**: catálogos, perfiles de usuario, contenido variable
- **Prototipado rápido**: schema que evoluciona frecuentemente
- **Escalabilidad horizontal masiva**: millones de documentos
- **Documentos jerárquicos**: árboles, objetos complejos

### Cuándo usar Redis

- **Cache de alta velocidad**: datos frecuentemente accedidos
- **Contadores atómicos**: vistas, likes, shares en tiempo real
- **Datos efímeros**: sesiones, tokens, rates limiting
- **Pub/Sub**: sistemas de mensajería, notificaciones

---

## 📝 ¿Cómo sería este proyecto en SQL?

Si hubiéramos usado PostgreSQL/MySQL en lugar de MongoDB:

### Schema SQL Propuesto

```sql
-- Tabla principal de contenidos
CREATE TABLE contenidos (
    id VARCHAR(36) PRIMARY KEY,
    titulo VARCHAR(255) NOT NULL,
    tipo VARCHAR(50) NOT NULL,  -- 'pelicula' o 'serie'
    director VARCHAR(255),
    anio_estreno INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tabla de géneros (relación many-to-many)
CREATE TABLE generos (
    id INT PRIMARY KEY AUTO_INCREMENT,
    nombre VARCHAR(50) UNIQUE
);

CREATE TABLE contenidos_generos (
    contenido_id VARCHAR(36),
    genero_id INT,
    PRIMARY KEY (contenido_id, genero_id),
    FOREIGN KEY (contenido_id) REFERENCES contenidos(id) ON DELETE CASCADE,
    FOREIGN KEY (genero_id) REFERENCES generos(id)
);

-- Tabla de actores (relación one-to-many)
CREATE TABLE actores (
    id INT PRIMARY KEY AUTO_INCREMENT,
    contenido_id VARCHAR(36) NOT NULL,
    nombre VARCHAR(255) NOT NULL,
    papel VARCHAR(255),
    FOREIGN KEY (contenido_id) REFERENCES contenidos(id) ON DELETE CASCADE
);

-- Tabla de metadatos (relación one-to-one)
CREATE TABLE metadatos (
    contenido_id VARCHAR(36) PRIMARY KEY,
    calidad VARCHAR(50),
    idiomas JSON,        -- PostgreSQL; TEXT en MySQL
    subtitulos JSON,
    FOREIGN KEY (contenido_id) REFERENCES contenidos(id) ON DELETE CASCADE
);

-- Tabla para ranking (en lugar de Redis Sorted Set)
CREATE TABLE ranking_vistas (
    contenido_id VARCHAR(36) PRIMARY KEY,
    vistas BIGINT DEFAULT 0,
    FOREIGN KEY (contenido_id) REFERENCES contenidos(id) ON DELETE CASCADE
);

-- Tabla para sesiones (en lugar de Redis Hash)
CREATE TABLE sesiones_usuario (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    last_content_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);
```

### Queries equivalentes

```sql
-- Buscar contenidos por género (equivalente a findByGenerosContaining)
SELECT c.* FROM contenidos c
JOIN contenidos_generos cg ON c.id = cg.contenido_id
JOIN generos g ON cg.genero_id = g.id
WHERE g.nombre = 'Ciencia Ficción';

-- Ranking Top 5
SELECT c.titulo, rv.vistas 
FROM ranking_vistas rv
JOIN contenidos c ON rv.contenido_id = c.id
ORDER BY rv.vistas DESC
LIMIT 5;

-- Incrementar vistas (equivalente a ZINCRBY)
UPDATE ranking_vistas 
SET vistas = vistas + 1 
WHERE contenido_id = 'contenido123';
```

### Comparación de rendimiento

| Operación | MongoDB + Redis | PostgreSQL |
|-----------|-----------------|------------|
| Buscar por género | 1 query | 3+ JOINs |
| Top 5 ranking | O(log N + N) | ORDER BY + LIMIT |
| Incrementar vista | ZINCRBY atómico | UPDATE (posible race condition) |
| Insertar contenido | 1 documento | 4-5 tablas |

---

## 💡 Conclusions y Recomendaciones

### Para este proyecto específico

**La elección de MongoDB + Redis es correcta porque:**

1. **Contenido**: Catálogo de streaming con schema variable (metadatos, elenco) - ideal para documentos embebidos
2. **Ranking**: Contadores de alta frecuencia - perfecto para Sorted Set
3. **Sesiones**: Datos efímeros con expiración - caso de uso clásico de Redis

### Errores comunes a evitar

1. **No usar Redis como base de datos principal**: Solo para cache/datos efímeros
2. **Abusar de documentos embebidos**: Si los datos crecen mucho, normalizá
3. **Ignorar la consistencia**: Redis no es ACID, no guardes datos críticos ahí

### Cuándo cambiaría la recomendación

- Si necesitás transacciones ACID → PostgreSQL
- Si tenés relaciones complejas entre entidades → SQL
- Si necesitás reporting complejo → SQL + herramientas analíticas

---

## 📦 Datos de Esquema

### MongoDB - Colección `contenidos`

```json
{
  "_id": "ObjectId",
  "titulo": "String",
  "tipo": "pelicula | serie",
  "generos": ["String"],
  "director": "String",
  "anioEstreno": "Number",
  "elenco": [
    { "nombre": "String", "papel": "String" }
  ],
  "metadatos": {
    "calidad": "String",
    "idiomas": ["String"],
    "subtitulos": ["String"]
  }
}
```

### Redis - Estructuras Utilizadas

| Estructura | Clave | Descripción |
|------------|-------|-------------|
| **Sorted Set** | `ranking:vistas:global` | Ranking de vistas (score = cantidad) |
| **Hash** | `user:{id}:session` | Sesión usuario (lastContentId, timestamp) |

---

## 🔌 Endpoints API

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| POST | `/api/contenidos` | Insertar contenido |
| GET | `/api/contenidos/genero/{genero}` | Buscar por género |
| PUT | `/api/contenidos/{id}/metadatos?calidad=` | Actualizar metadatos |
| POST | `/api/vistas/{contenidoId}` | Incrementar vistas |
| GET | `/api/ranking/top5` | Ver top 5 |
| POST | `/api/sesion/{userId}?contenidoId=` | Guardar sesión |
| GET | `/api/sesion/{userId}` | Obtener sesión |

---

## 🖥️ Guía de Ejecución

### Prerrequisitos

- JDK 17+
- Maven 3.x
- Docker (para MongoDB y Redis)

### Paso 1: Levantar MongoDB y Redis

```bash
# Crear red
docker network create streaming-net

# MongoDB
docker run -d -p 27017:27017 --name mongodb --network streaming-net mongo:latest

# Redis
docker run -d -p 6379:6379 --name redis --network streaming-net redis:latest
```

### Paso 2: Compilar el Proyecto

```bash
cd proyecto
mvn clean package
```

### Paso 3: Ejecutar la App

```bash
# Con Maven
mvn spring-boot:run

# O ejecutando el JAR
java -jar target/streaming-app-1.0.0.jar
```

### Paso 4: Acceder

- **API**: http://localhost:8080/api
- **Frontend Test**: http://localhost:8080

---

## 🧪 Comandos de Prueba

### Redis

```bash
# Incrementar vistas
ZINCRBY ranking:vistas:global 1 contenido:123

# Top 5 (sintaxis moderna)
ZRANGE ranking:vistas:global 0 4 REV WITHSCORES

# Guardar sesión con TTL
HSET user:user1:session lastContentId "contenido123" timestamp 1751821800
EXPIRE user:user1:session 3600

# Obtener sesión
HGETALL user:user1:session
```

### MongoDB

```javascript
// Insertar
db.contenidos.insertOne({ 
  titulo: "Inception", 
  tipo: "pelicula", 
  generos: ["Ciencia Ficción"], 
  director: "Christopher Nolan", 
  anioEstreno: 2010, 
  metadatos: { calidad: "4K", idiomas: ["Inglés"], subtitulos: ["Español"] } 
})

// Buscar por género
db.contenidos.find({ generos: "Ciencia Ficción" })

// Actualizar metadatos
db.contenidos.updateOne({ _id: ObjectId("...") }, { $set: { "metadatos.calidad": "8K" } })
```

---

## 📚 Notas Importantes

- La sesión de usuario tiene TTL de 3600 segundos (1 hora)
- El ranking usa Redis Sorted Set para operaciones atómicas
- El timestamp se guarda como Unix Timestamp (Long)
- MongoDB usa documentos embebidos para Actor y Metadatos

---

## 🤝 Contribuir

1. Fork del repositorio
2. Crear branch (`git checkout -b feature/nueva-funcionalidad`)
3. Commit cambios (`git commit -m 'Agregar nueva funcionalidad'`)
4. Push al branch (`git push origin feature/nueva-funcionalidad`)
5. Crear Pull Request

---

## 📜 Licencia

MIT