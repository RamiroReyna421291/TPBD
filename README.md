# Streaming App - Sistema de Gestión de Contenidos

## Descripción

API RESTful para sistema de streaming que utiliza **MongoDB** como base de datos principal y **Redis** para cache y tiempo real.

## Arquitectura

```
┌─────────────┐     ┌─────────────┐
│   Frontend  │────▶│  Spring Boot│
│  (HTML/JS)  │     │    App      │
└─────────────┘     └──────┬──────┘
                          │
           ┌──────────────┼──────────────┐
           │              │              │
           ▼              ▼              ▼
     ┌───────────┐ ┌───────────┐ ┌───────────┐
     │  MongoDB   │ │   Redis   │ │  Tomcat   │
     │ (Persistencia) │(Ranking)│ │ (Puerto 8080) │
     └───────────┘ └───────────┘ └───────────┘
```

## Tecnologías

| Componente | Tecnología | Versión |
|------------|------------|---------|
| Backend | Spring Boot | 3.2.0 |
| Base de datos | MongoDB | Latest |
| Cache/Ranking | Redis | Latest |
| Build | Maven | 3.x |
| Java | JDK | 17+ |

## Estructura del Proyecto

```
src/main/java/com/streaming/
├── StreamingApplication.java      # Main class
├── config/
│   └── RedisConfig.java          # Config Redis ZSetOperations
├── controller/
│   └── StreamingController.java  # Endpoints REST
├── model/
│   ├── Contenido.java            # Documento MongoDB
│   └── UserSession.java          # Entidad Redis Hash
├── repository/
│   └── ContenidoRepository.java  # Repository MongoDB
└── service/
    └── StreamingService.java      # Lógica de negocio

src/main/resources/
├── application.yml                # Configuración
└── static/
    └── index.html                 # Frontend de prueba
```

## Esquemas de Datos

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

### Redis

| Estructura | Clave | Descripción |
|------------|-------|-------------|
| **Sorted Set** | `ranking:vistas:global` | Ranking de vistas (score = cantidad) |
| **Hash** | `user:{id}:session` | Sesión usuario (lastContentId, timestamp) |

## Endpoints API

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| POST | `/api/contenidos` | Insertar contenido |
| GET | `/api/contenidos/genero/{genero}` | Buscar por género |
| PUT | `/api/contenidos/{id}/metadatos?calidad=` | Actualizar metadatos |
| POST | `/api/vistas/{contenidoId}` | Incrementar vistas |
| GET | `/api/ranking/top5` | Ver top 5 |
| POST | `/api/sesion/{userId}?contenidoId=` | Guardar sesión |
| GET | `/api/sesion/{userId}` | Obtener sesión |

## Comandos Redis

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

## Comandos MongoDB

```javascript
// Insertar
db.contenidos.insertOne({ titulo: "Inception", tipo: "pelicula", generos: ["Ciencia Ficción"], director: "Christopher Nolan", anioEstreno: 2010, metadatos: { calidad: "4K", idiomas: ["Inglés"], subtitulos: ["Español"] } })

// Buscar por género
db.contenidos.find({ generos: "Ciencia Ficción" })

// Actualizar metadatos
db.contenidos.updateOne({ _id: ObjectId("...") }, { $set: { "metadatos.calidad": "8K" } })
```

---

## Guía de Deployment

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

## Desarrollo

### Compilar

```bash
mvn clean compile
```

### Ejecutar Tests

```bash
mvn test
```

### Generar JAR

```bash
mvn clean package -DskipTests
```

---

## Notas

- La sesión de usuario tiene TTL de 3600 segundos (1 hora)
- El ranking usa sintaxis moderna de Redis (`ZRANGE ... REV`)
- El timestamp se guarda como Unix Timestamp (Long)

## Contribuir

1. Fork del repositorio
2. Crear branch (`git checkout -b feature/nueva-funcionalidad`)
3. Commit cambios (`git commit -m 'Agregar nueva funcionalidad'`)
4. Push al branch (`git push origin feature/nueva-funcionalidad`)
5. Crear Pull Request

---

## Licencia

MIT