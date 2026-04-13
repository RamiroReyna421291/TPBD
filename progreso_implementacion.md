# Registro de Implementación: Backend Streaming

## 1. Persistencia de Usuarios (MongoDB)
En esta etapa, vamos a crear el equivalente a un `DbSet<User>` en EF Core.

### Conceptos Clave (C# vs Java)
- **Entidad:** En C# usarías `[Table("Users")]` y `[Key]`. En Spring Data MongoDB usamos `@Document(collection = "users")` y `@Id`.
- **Repositorio:** En C# implementarías un patrón Repository manualmente o usarías el `DbContext`. En Java, solo definimos una interfaz que hereda de `MongoRepository` y Spring genera la implementación en tiempo de ejecución.

### Cambios realizados:
- **Modelo `User`:** Creado en `com.streaming.model`.
- **Repositorio `UserRepository`:** Creado en `com.streaming.repository`.
- **Seguridad (BCrypt):** Las contraseñas ahora se guardan hasheadas con BCrypt.
- **Autenticación (JWT):** El login ahora genera un token JWT para el cliente.
- **Servicio `UserService`:** Orquesta MongoDB, Redis (sesión) y Seguridad (BCrypt + JWT).
- **Controlador `AuthController`:** Actualizado para devolver Token JWT en el login.
- **Infraestructura:** `docker-compose.yml` y `Dockerfile` operativos.
- **Documentación:** Creada `docs/hoja_de_ruta_datos.md` detallando el flujo de datos.

---
**Estado:** Seguridad Implementada y Verificada.
