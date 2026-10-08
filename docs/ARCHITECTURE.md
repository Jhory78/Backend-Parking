# Arquitectura del backend

**Arquitectura hexagonal (puertos y adaptadores)** organizada por capas.

```
src/main/java/com/parking
├── ParkingApplication.java
├── domain/                         Núcleo: reglas de negocio. Java puro (+ anotaciones JPA)
│   ├── entity/                     User, ParkingSpace, Occupancy, OccupancyReport, AccessToken, enums...
│   └── exception/                  BusinessRuleException (409), ResourceNotFoundException (404) y sus hijas
├── application/                    Casos de uso. No conoce HTTP, Spring Security ni JWT
│   ├── port/
│   │   ├── in/                     Puertos de entrada: *UseCase (interfaces) y *Command
│   │   └── out/                    Puertos de salida: *Port (interfaces)
│   └── usecase/                    Implementaciones: UserService, AuthService, SpaceService
└── infrastructure/                 Adaptadores y detalles técnicos
    ├── adapter/
    │   ├── in/rest/                Adaptador de entrada HTTP
    │   │   ├── controller/         AuthController, UserController, SpaceController
    │   │   ├── dto/                Records de entrada/salida con validaciones (@Valid)
    │   │   ├── exception/          ApiError, GlobalExceptionHandler
    │   │   └── mapper/             DTO ↔ comando / dominio
    │   └── out/                    Adaptadores de salida (implementan los *Port)
    │       ├── persistence/        Spring Data JPA
    │       ├── notification/       Tiempo real con Server-Sent Events
    │       └── barrier/            Barrera de ingreso (simulada)
    ├── config/                     Beans y @ConfigurationProperties
    ├── security/                   Spring Security, JWT, BCrypt
    └── utils/constants/            ApiRoutes, SecurityConstants
```

## Flujo de una petición

```
HTTP ─► controller ─► mapper ─► *UseCase (port/in) ─► usecase/*Service ─► domain
                                                            │
                                                            └─► *Port (port/out) ◄── adapter/out, security
```

Ejemplo, `PATCH /spaces/{id}/status`:
1. `SpaceController` valida el `UpdateSpaceStatusRequest` y llama a `ChangeSpaceStatusUseCase`.
2. `SpaceService` carga el espacio por `SpaceRepositoryPort`, aplica la regla `parkingSpace.occupy()` del dominio,
   calcula la ocupación (`OccupancyReport`) y avisa por `EntryBarrierPort` y `OccupancyNotifierPort`.
3. `SpacePersistenceAdapter`, `SimulatedEntryBarrierAdapter` y `SseOccupancyNotifierAdapter` hacen el trabajo técnico.
4. `SpaceRestMapper` convierte el resultado en `SpaceResponse`.

## Reglas de dependencias

| Capa | Puede usar | No puede usar |
|---|---|---|
| `domain` | Java, JPA, Lombok | `application`, `infrastructure`, Spring |
| `application` | `domain` | `infrastructure`, HTTP, Spring Security, JWT |
| `infrastructure` | todo | — |

Además:
- Los controllers usan **solo puertos de entrada** (`*UseCase`), nunca `usecase/*Service` ni repositorios.
- Los puertos de entrada (`*UseCase`) y de salida (`*Port`) son interfaces.
- El usuario autenticado llega a los controllers con `@AuthenticationPrincipal`.

**Estas reglas se verifican automáticamente** en `HexagonalArchitectureTest` (ArchUnit): si alguien las rompe,
el build falla.

Concesión práctica: las entidades de `domain/entity` llevan anotaciones JPA, para no duplicar cada entidad
con su versión de persistencia más un mapper.

## Inventario por funcionalidad

| Funcionalidad | Puertos de entrada | Caso de uso | Puertos de salida | Adaptadores |
|---|---|---|---|---|
| Login (RF-40..42) | `LoginUseCase` | `AuthService` | `CredentialsAuthenticatorPort`, `AccessTokenIssuerPort` | `SpringSecurityCredentialsAuthenticator`, `JwtTokenService`, `AuthController` |
| Cuentas (RF-43..45) | `CreateUserUseCase`, `UpdateUserUseCase`, `ChangeUserStatusUseCase`, `FindUsersUseCase` | `UserService` | `UserRepositoryPort`, `PasswordHasherPort` | `UserPersistenceAdapter`, `BCryptPasswordHasher`, `UserController` |
| Espacios (RF-16..22) | `FindSpacesUseCase`, `ChangeSpaceStatusUseCase` | `SpaceService` | `SpaceRepositoryPort`, `OccupancyNotifierPort`, `EntryBarrierPort` | `SpacePersistenceAdapter`, `SseOccupancyNotifierAdapter`, `SimulatedEntryBarrierAdapter`, `SpaceController` |

## Cómo agregar un caso de uso (ej. Sprint 3: registrar ingreso)

1. **Dominio:** `domain/entity/EntryRecord`, reglas como métodos de la entidad; excepciones en `domain/exception`
   extendiendo `BusinessRuleException` o `ResourceNotFoundException`.
2. **Puerto de entrada:** `application/port/in/RegisterEntryUseCase` + `RegisterEntryCommand`.
3. **Puertos de salida:** lo que necesita del exterior, en `application/port/out/`:
   `PlateRecognizerPort`, `PlateCameraPort`, `EntryRecordRepositoryPort`...
4. **Caso de uso:** `application/usecase/EntryService implements RegisterEntryUseCase`.
   Para ocupar el espacio reutiliza `ChangeSpaceStatusUseCase`: el tiempo real, las alertas y la barrera funcionan sin cambios.
5. **Adaptadores:** `adapter/in/rest` (controller, DTOs, mapper) y `adapter/out` (persistencia; para el
   hardware uno simulado y uno real seleccionados con `@Profile`).
6. **Migración Flyway** si hay tablas nuevas.
7. **Tests:** unitarios del dominio, del caso de uso con adaptadores en memoria (ver `UserServiceTest`) y una
   clase de aceptación con un `@Nested` por RF.

## Convenciones

| Tema | Regla |
|---|---|
| Idioma del código | Inglés: paquetes, clases, métodos, variables, columnas de BD y campos JSON. |
| Mensajes al usuario | Español: los muestra el frontend (`message` y `fieldErrors` de `ApiError`). |
| Nombres | `*UseCase` (puerto de entrada), `*Command` (datos de entrada), `*Service` (caso de uso), `*Port` (puerto de salida), `*Adapter` (adaptador de salida), `*Request`/`*Response` (DTO), `*RestMapper`. |
| Rutas REST | En `ApiRoutes`. Sustantivos en plural e inglés: `/users`, `/spaces/{spaceId}/status`. |
| Errores | Excepciones del dominio; `GlobalExceptionHandler` las traduce. Nunca construir respuestas de error a mano. |
| Configuración | `@ConfigurationProperties` con records en `infrastructure/config`, no `@Value` repartidos. Secretos en `.env`. |
| Inyección | Por constructor (`@RequiredArgsConstructor`), campos `final`. |
| Seguridad | Endpoints protegidos por defecto (públicos en `SecurityConstants.PUBLIC_ENDPOINTS`). Rol con `@PreAuthorize("hasRole('ADMIN')")`. |

## Base de datos: Flyway

El esquema lo controla **Flyway**; Hibernate solo lo valida (`ddl-auto: validate`).

- Toda entidad o columna nueva necesita su migración en `src/main/resources/db/migration` (`V4__...`).
  **Sin migración la app no arranca.**
- Nunca se edita una migración ya aplicada: se crea una nueva.
- El SQL debe funcionar en PostgreSQL y en H2 (modo PostgreSQL), que es la base de los tests.

## Tests

La carpeta `src/test` replica los paquetes de `src/main`.

| Tipo | Clases | Velocidad |
|---|---|---|
| Arquitectura | `HexagonalArchitectureTest` (8 reglas de dependencias) | ms |
| Dominio | `domain/entity/*Test` | ms |
| Casos de uso | `application/usecase/UserServiceTest`, `AuthServiceTest` (adaptadores en memoria, sin Spring) | ms |
| Aceptación | `SprintOneAcceptanceTest` (RF-40..45), `SprintTwoAcceptanceTest` (RF-16..22): API real con MockMvc | s |
| Migraciones | `infrastructure/adapter/out/persistence/UsersSchemaMigrationTest` | s |

> En Windows, si `mvn clean` no logra borrar `target/` (la extensión Java de VS Code bloquea archivos),
> pueden quedar `.class` de paquetes eliminados y Spring falla con `ConflictingBeanDefinitionException`.
> Solución: cerrar el IDE o borrar `target/classes`.
