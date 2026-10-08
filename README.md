# Parking API

Backend del sistema de gestión de parqueadero — Grupo 5 (Desarrollo 2).

API REST con autenticación JWT, gestión de cuentas de operarios y monitoreo de ocupación de
espacios en tiempo real. Arquitectura hexagonal: ver [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

| | |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 4 (Web MVC, Data JPA, Security, Validation) |
| Base de datos | PostgreSQL 16, migraciones con Flyway |
| Autenticación | JWT (jjwt) |
| Tiempo real | Server-Sent Events |
| Documentación | OpenAPI / Swagger UI |
| Tests | JUnit 5, MockMvc, H2, ArchUnit |

## Puesta en marcha

### Con Docker (recomendado)

```bash
cp .env.example .env      # y completar los valores
docker compose up -d --build
```

La API queda en `http://localhost:9200/pk/api` y Swagger UI en
`http://localhost:9200/pk/api/swagger-ui/index.html`.

Después de cualquier cambio en el código hay que volver a ejecutar `docker compose up -d --build`:
sin `--build` se ejecuta la imagen anterior.

### En local

Requiere Java 21 y una base PostgreSQL (puede ser solo el contenedor de base de datos):

```bash
docker compose up -d db
./mvnw spring-boot:run
```

Las credenciales no tienen valor por defecto: la aplicación lee `POSTGRES_PASSWORD`, `APP_JWT_SECRET` y
`APP_ADMIN_PASSWORD` del entorno o del archivo `.env` (creado desde `.env.example`, ver más abajo) y no
arranca si faltan.

### Cuenta inicial

Al arrancar se crea, si no existe, el administrador definido en `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD`
(el correo por defecto es `admin@parking.com`; la contraseña no tiene valor por defecto).

## Variables de entorno

| Variable | Descripción | Por defecto |
|---|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Base de datos | `parking_db`, `parking_user`, — |
| `POSTGRES_PORT` | Puerto de Postgres en el host | `5433` |
| `APP_PORT` | Puerto de la API en el host | `9200` |
| `APP_JWT_SECRET` | Clave HMAC Base64 (≥ 256 bits): `openssl rand -base64 32` | — |
| `APP_JWT_EXPIRATION` | Vigencia del token | `24h` |
| `APP_ADMIN_NAME`, `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD` | Administrador inicial | —, `admin@parking.com`, — |
| `APP_CORS_ALLOWED_ORIGINS` | Orígenes del frontend | `http://localhost:4200` |
| `APP_SPACES_STREAM_TIMEOUT` | Duración de cada conexión en tiempo real | `30m` |

## Seguridad

- `.env` está en `.gitignore`: nunca se sube. Solo se versiona `.env.example`, con valores de ejemplo.
- Genere un `APP_JWT_SECRET` propio (`openssl rand -base64 32`) y contraseñas distintas para cada entorno.
- Los tests usan `src/test/resources/application.yml` con valores exclusivos de prueba (H2 en memoria).

## Endpoints

Todas las rutas son relativas a `/pk/api` y, salvo login y logout, requieren `Authorization: Bearer <token>`.

| Método | Ruta | Rol | RF |
|---|---|---|---|
| `POST` | `/auth/login` | público | 40, 41 |
| `GET` | `/auth/me` | autenticado | 42 |
| `POST` | `/auth/logout` | público | — |
| `GET` | `/users`, `/users/{id}` | ADMIN | 42 |
| `POST` | `/users` | ADMIN | 43 |
| `PUT` | `/users/{id}` | ADMIN | 44 |
| `PATCH` | `/users/{id}/status` | ADMIN | 45 |
| `GET` | `/spaces?vehicleType=&status=` | autenticado | 18 |
| `GET` | `/spaces/availability?vehicleType=` | autenticado | 17 |
| `GET` | `/spaces/occupancy` | autenticado | 19, 21, 22 |
| `PATCH` | `/spaces/{id}/status` | autenticado | 16 |
| `GET` | `/spaces/stream` (SSE) | autenticado | 16, 20 |

Los errores siempre tienen el formato `ApiError`: `timestamp`, `status`, `error`, `message`, `path` y,
en errores de validación, `fieldErrors`.

## Tests

```bash
./mvnw test
```

Incluyen pruebas de aceptación por historia de usuario, tests unitarios del dominio y de los casos de uso,
pruebas de migraciones y las reglas de arquitectura (`HexagonalArchitectureTest`).
