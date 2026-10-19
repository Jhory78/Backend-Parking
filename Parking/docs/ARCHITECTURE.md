# Arquitectura del backend

**Arquitectura hexagonal (puertos y adaptadores)** organizada por capas.

```
src/main/java/com/parking
├── ParkingApplication.java
├── domain/                         Núcleo: reglas de negocio. Java puro (+ anotaciones JPA)
│   ├── entity/                     User, ParkingSpace, VehicleEntry, LicensePlate, Occupancy, AccessToken, enums...
│   └── exception/                  BusinessRuleException (409), ResourceNotFoundException (404) y sus hijas
├── application/                    Casos de uso. No conoce HTTP, Spring Security ni JWT
│   ├── port/
│   │   ├── in/                     Puertos de entrada: *UseCase (interfaces) y *Command
│   │   └── out/                    Puertos de salida: *Port (interfaces)
│   └── usecase/                    Implementaciones: UserService, AuthService, SpaceService, EntryService
└── infrastructure/                 Adaptadores y detalles técnicos
    ├── adapter/
    │   ├── in/rest/                Adaptador de entrada HTTP
    │   │   ├── controller/         AuthController, UserController, SpaceController, EntryController
    │   │   ├── dto/                Records de entrada/salida con validaciones (@Valid)
    │   │   ├── exception/          ApiError, GlobalExceptionHandler
    │   │   └── mapper/             DTO ↔ comando / dominio
    │   └── out/                    Adaptadores de salida (implementan los *Port)
    │       ├── persistence/        Spring Data JPA
    │       ├── notification/       Tiempo real con Server-Sent Events
    │       ├── barrier/            Barrera de ingreso (simulada)
    │       └── ocr/                Reconocimiento de placas (Plate Recognizer API)
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
| Espacios (RF-16..22) | `FindSpacesUseCase`, `ChangeSpaceStatusUseCase`, `SpaceAllocationUseCase` | `SpaceService` | `SpaceRepositoryPort`, `OccupancyNotifierPort`, `EntryBarrierPort` | `SpacePersistenceAdapter`, `SseOccupancyNotifierAdapter`, `SimulatedEntryBarrierAdapter`, `SpaceController` |
| Ingresos y salidas (Sprint 3: RF-01..11) | `RecognizePlateUseCase`, `RegisterEntryUseCase`, `RegisterExitUseCase`, `FindEntriesUseCase` | `EntryService` (usa `SpaceAllocationUseCase`) | `PlateRecognitionPort`, `VehicleEntryRepositoryPort` | `PlateRecognizerAdapter`, `VehicleEntryPersistenceAdapter`, `EntryController` |

### Ingresos y salidas (Sprint 3)

- **El OCR solo propone.** `POST /entries/recognize` no guarda nada: el operario confirma o corrige la placa y
  `POST /entries` registra el ingreso. Si el OCR falla responde 422 y el operario escribe la placa (RF-03).
- **`LicensePlate`** (dominio) normaliza (`abc-123` → `ABC123`), infiere el tipo por el formato colombiano
  (carro `ABC123`, moto `ABC12D`) y corrige los errores típicos del OCR por posición (O/0, I/1, B/8, S/5, Z/2).
- **Asignación de plaza:** `EntryService` pide la plaza a `SpaceAllocationUseCase` (implementado por `SpaceService`),
  así el tiempo real, las alertas y la barrera son los mismos que con el `PATCH` manual.
- **Concurrencia:** `SpaceRepositoryPort.lockFirstAvailable` bloquea (`SELECT ... FOR UPDATE`) todas las plazas
  del tipo en orden de código. Bloquear solo "la primera libre" con `LIMIT 1` no sirve: la transacción que espera
  la encuentra ocupada y no recibe ninguna, aunque haya otras libres.
- **Una placa, un ingreso activo:** se valida en `EntryService` y, por si dos peticiones llegan a la vez, con la
  columna `vehicle_entries.active_plate` (`UNIQUE`; vale la placa mientras el ingreso está activo y `NULL` después).
- **Cambiar de motor OCR:** escribir otro adaptador de `PlateRecognitionPort` (por ejemplo un microservicio Python
  con PaddleOCR) y retirar `@Component` de `PlateRecognizerAdapter` o elegir uno con `@Profile`.

## Cómo agregar un caso de uso

Ejemplo real: `EntryService` (Sprint 3).

1. **Dominio:** entidades en `domain/entity` con las reglas como métodos (`VehicleEntry.close`, `LicensePlate.of`);
   excepciones en `domain/exception` extendiendo `BusinessRuleException` (409), `ResourceNotFoundException` (404)
   o `FieldValidationException` (400 con `fieldErrors`).
2. **Puerto de entrada:** `application/port/in/RegisterEntryUseCase` + `RegisterEntryCommand`.
3. **Puertos de salida:** lo que necesita del exterior, en `application/port/out/`:
   `PlateRecognitionPort`, `VehicleEntryRepositoryPort`...
4. **Caso de uso:** `application/usecase/EntryService implements RegisterEntryUseCase`. Si necesita otro caso de
   uso, lo usa por su puerto de entrada (`SpaceAllocationUseCase`), nunca por la clase `*Service`.
5. **Adaptadores:** `adapter/in/rest` (controller, DTOs, mapper) y `adapter/out` (persistencia, servicios externos;
   para el hardware uno simulado y uno real seleccionados con `@Profile`).
6. **Migración Flyway** si hay tablas nuevas.
7. **Tests:** unitarios del dominio, del caso de uso con adaptadores en memoria (ver `UserServiceTest`) y una
   clase de aceptación con un `@Nested` por RF. Los servicios externos se reemplazan con `@MockitoBean`.

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
| Dominio | `domain/entity/*Test` (incluye `LicensePlateTest`: normalización, formatos y corrección del OCR) | ms |
| Casos de uso | `application/usecase/UserServiceTest`, `AuthServiceTest` (adaptadores en memoria, sin Spring) | ms |
| Aceptación | `SprintOneAcceptanceTest` (RF-40..45), `SprintTwoAcceptanceTest` (RF-16..22), `SprintThreeAcceptanceTest` (RF-01..11, OCR simulado con `@MockitoBean`, incluye una prueba de concurrencia): API real con MockMvc | s |
| Migraciones | `infrastructure/adapter/out/persistence/UsersSchemaMigrationTest`, `VehicleEntriesSchemaMigrationTest` | s |

> En Windows, si `mvn clean` no logra borrar `target/` (la extensión Java de VS Code bloquea archivos),
> pueden quedar `.class` de paquetes eliminados y Spring falla con `ConflictingBeanDefinitionException`.
> Solución: cerrar el IDE o borrar `target/classes`.
> Lo mismo si los tests fallan con `ClassFormatError` o `Unresolved compilation problem`: el IDE compiló sobre
> `target/classes` mientras se editaba un archivo. Borrar los archivos de `target/` y volver a ejecutar.
