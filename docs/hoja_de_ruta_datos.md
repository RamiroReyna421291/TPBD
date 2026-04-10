# Hoja de Ruta: El Camino de los Datos (C# -> Java)

Este documento detalla el ciclo de vida de los datos de un usuario desde que llegan al backend hasta que se persisten y se gestiona su sesión.

## 1. Entrada de Datos (Controller)
**Archivo:** `AuthController.java`
- Los datos llegan en formato JSON (por ejemplo, `{"username": "valen", "password": "123"}`).
- El `@RestController` (equivalente a `[ApiController]`) recibe el objeto `User` en el cuerpo de la petición (`@RequestBody`).
- **Responsabilidad:** Validar el formato y delegar al servicio.

## 2. Orquestación y Lógica de Negocio (Service)
**Archivo:** `UserService.java`
Aquí es donde ocurre la "magia" de las dos bases de datos:
- **Paso 2.1 - Consulta en MongoDB:** El servicio llama al `UserRepository` (nuestro `DbSet`) para ver si el usuario existe.
- **Paso 2.2 - Verificación de Seguridad (BCrypt):** Se compara el hash de la contraseña (no la contraseña en texto plano).
- **Paso 2.3 - Creación de Sesión en Redis:** Si el login es exitoso, el servicio le pide al `UserSessionRepository` que cree una entrada en Redis.
- **Paso 2.4 - Generación de Token (JWT):** Se genera un token firmado que el usuario llevará en cada petición futura (como su "DNI" digital).

## 3. Persistencia (Repositories)
**Archivos:** `UserRepository.java` (MongoDB) y `UserSessionRepository.java` (Redis)
- **MongoDB:** Guarda el perfil permanente. Los datos viajan por el driver de Mongo y se guardan como un documento BSON.
- **Redis:** Guarda la sesión activa. Los datos viajan por `RedisTemplate` y se guardan como un Hash con un tiempo de vida (TTL).

## 4. Respuesta al Cliente
- El backend devuelve el objeto `User` (sin la contraseña) y el `JWT token`.
- El cliente (Front) guarda el token para enviarlo en el header `Authorization` de las siguientes llamadas.

---
*Documentado por: Prompt Engineer Agent*
