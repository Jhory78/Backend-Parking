# Contrato API: ingreso y salida de vehículos por placa (Sprint 3)

Para el equipo de frontend. Cubre las historias RF-01 a RF-11 y la salida de vehículos.

## Generalidades

- **Base URL:** `http://localhost:9200/pk/api`. Todas las rutas de abajo son relativas a ella.
- **Autenticación:** `Authorization: Bearer <accessToken>` en todas las peticiones. Pueden usarlas `ADMIN` y `OPERATOR`.
- **Fechas:** ISO-8601 en UTC con segundos, por ejemplo `2026-10-14T15:30:00Z`. El frontend las convierte a hora local.
- **Placas:** el backend las guarda y responde normalizadas: mayúsculas, sin espacios ni guiones (`abc-123` → `ABC123`).
  Para mostrarlas con guion (`ABC-123`), el formato lo aplica el frontend.
- **Enums:** `vehicleType` = `CAR | MOTORCYCLE`, `status` del ingreso = `ACTIVE | CLOSED`.
- **Errores:** siempre en el formato común. `fieldErrors` aparece solo en los errores de validación.

```json
{
  "timestamp": "2026-10-14T15:30:00.123Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Error de validación en los datos enviados",
  "path": "/entries",
  "fieldErrors": { "plate": "La placa es obligatoria" }
}
```

## Flujo

```
Ingreso:  foto ──► POST /entries/recognize ──► el operario confirma o corrige ──► POST /entries
                        │ 422
                        └──► formulario manual ─────────────────────────────────► POST /entries
Salida:   (foto opcional) ──► POST /entries/exit
```

1. `POST /entries/recognize` lee la placa de la foto. **No registra nada.**
2. El operario confirma o corrige la placa, y `POST /entries` registra el ingreso y **asigna la plaza automáticamente**.
3. Para la salida, `POST /entries/exit` cierra el ingreso y libera la plaza.

Cada ingreso o salida emite el evento SSE `occupancy` en `GET /spaces/stream`, el mismo de Sprint 2. No hay un
stream nuevo: el mapa y la alerta de ocupación se actualizan solos.

---

## `POST /entries/recognize`: leer la placa de una foto

`multipart/form-data` con el campo **`image`** (JPEG o PNG, máximo 5 MB).

```
POST /entries/recognize
Content-Type: multipart/form-data; boundary=...

image=<archivo>
```

En Angular:

```ts
const form = new FormData();
form.append('image', file);            // File de <input type="file" accept="image/jpeg,image/png" capture="environment">
this.http.post<PlateRecognitionResponse>(`${API}/entries/recognize`, form);   // sin fijar Content-Type a mano
```

**200 OK**

```json
{
  "plate": "ABC123",
  "confidence": 0.93,
  "lowConfidence": false,
  "vehicleType": "CAR",
  "validFormat": true,
  "alreadyInside": false
}
```

| Campo | Significado | Qué hacer en la UI |
|---|---|---|
| `plate` | Placa normalizada, con los errores típicos del OCR ya corregidos (O/0, I/1, B/8, S/5, Z/2) | Precargarla en un campo editable |
| `confidence` | Confianza del OCR, de 0 a 1 | — |
| `lowConfidence` | `true` si `confidence` < 0.80 (configurable en el backend) | Resaltar el campo y pedir que la revise |
| `vehicleType` | Tipo inferido del formato; `null` si el formato no es colombiano | Si es `null`, pedir el tipo |
| `validFormat` | La placa tiene formato de carro (`ABC123`) o de moto (`ABC12D`) | Si es `false`, pedir el tipo |
| `alreadyInside` | La placa ya tiene un ingreso activo | Avisar antes de confirmar (el ingreso daría 409) |

**Errores**

| Código | `message` | `fieldErrors` | Cuándo |
|---|---|---|---|
| 400 | `Error de validación en los datos enviados` | `image`: `La imagen es obligatoria` | Archivo vacío |
| 400 | `Error de validación en los datos enviados` | `image`: `El archivo es obligatorio` | Falta el campo `image` |
| 400 | `Error de validación en los datos enviados` | `image`: `La imagen no puede superar 5 MB` | Más de 5 MB |
| 400 | `Error de validación en los datos enviados` | `image`: `La imagen debe ser JPEG o PNG` | Otro formato (se revisa el contenido, no la extensión) |
| 413 | `El archivo supera el tamaño máximo permitido` | — | Más de 10 MB (límite duro del servidor) |
| 415 | `Tipo de contenido no soportado` | — | La petición no es `multipart/form-data` |
| 422 | `No se pudo leer la placa, ingrésala manualmente` | — | El OCR no encontró placa, falló o no está configurado. **Pasar al ingreso manual** |
| 401 | `Se requiere autenticación para acceder a este recurso` | — | Sin token o token vencido |

> Conviene validar en el frontend el tipo (JPEG/PNG) y el tamaño (≤ 5 MB) antes de subir la foto, y reducir la
> resolución si la cámara del celular genera archivos más grandes.

---

## `POST /entries`: registrar un ingreso

**Request**

```json
{ "plate": "ABC-123", "vehicleType": "CAR" }
```

- `plate`: obligatoria, en cualquier formato (`abc-123`, `ABC 123`...). Después de normalizarla debe tener entre 4 y 8 letras o números.
- `vehicleType`: **opcional** si la placa tiene formato colombiano (carro `ABC123`, moto `ABC12D`). **Obligatorio**
  en otro caso (placas extranjeras, diplomáticas...). Si se envía, tiene prioridad sobre el tipo inferido.

**201 Created**

```json
{
  "id": 15,
  "plate": "ABC123",
  "vehicleType": "CAR",
  "space": { "id": 3, "code": "C-03" },
  "entryTime": "2026-10-14T15:30:00Z",
  "exitTime": null,
  "durationMinutes": null,
  "status": "ACTIVE"
}
```

La plaza asignada es la primera libre del tipo, en orden de código (`C-01`, `C-02`...). Dos ingresos simultáneos
nunca reciben la misma plaza.

**Errores**

| Código | `message` | `fieldErrors` | Cuándo |
|---|---|---|---|
| 400 | `Error de validación en los datos enviados` | `plate`: `La placa es obligatoria` | Placa vacía o sin letras ni números |
| 400 | `Error de validación en los datos enviados` | `plate`: `La placa debe tener entre 4 y 8 letras o números` | Placa demasiado corta o larga |
| 400 | `Error de validación en los datos enviados` | `vehicleType`: `No se reconoce el formato de la placa; indica el tipo de vehículo` | Formato no colombiano y sin `vehicleType` |
| 400 | `El cuerpo de la petición no es válido` | — | `vehicleType` distinto de `CAR`/`MOTORCYCLE`, o JSON mal formado |
| 409 | `El vehículo ABC123 ya se encuentra dentro` | — | La placa ya tiene un ingreso activo (RF-11) |
| 409 | `No hay espacios disponibles para carros` | — | No hay plaza libre de carro, o la barrera está bloqueada (RF-06..08) |
| 409 | `No hay espacios disponibles para motos` | — | Igual, para motos |

---

## `POST /entries/exit`: registrar una salida

**Request**

```json
{ "plate": "ABC123" }
```

**200 OK**: el mismo `EntryResponse`, ya cerrado.

```json
{
  "id": 15,
  "plate": "ABC123",
  "vehicleType": "CAR",
  "space": { "id": 3, "code": "C-03" },
  "entryTime": "2026-10-14T15:30:00Z",
  "exitTime": "2026-10-14T17:05:00Z",
  "durationMinutes": 95,
  "status": "CLOSED"
}
```

`durationMinutes` son minutos completos (95 minutos y 59 segundos son `95`). El cobro todavía no está implementado.

**Errores**

| Código | `message` | `fieldErrors` | Cuándo |
|---|---|---|---|
| 400 | `Error de validación en los datos enviados` | `plate`: `La placa es obligatoria` | Placa vacía |
| 400 | `Error de validación en los datos enviados` | `plate`: `La placa debe tener entre 4 y 8 letras o números` | Placa demasiado corta o larga |
| 404 | `No hay un ingreso activo para la placa ABC123` | — | La placa no está dentro |

---

## `GET /entries/active`: vehículos dentro

**200 OK**: lista de `EntryResponse` con `status: "ACTIVE"`, **del ingreso más reciente al más antiguo**.

```json
[
  {
    "id": 16,
    "plate": "XYZ12A",
    "vehicleType": "MOTORCYCLE",
    "space": { "id": 21, "code": "M-01" },
    "entryTime": "2026-10-14T15:42:10Z",
    "exitTime": null,
    "durationMinutes": null,
    "status": "ACTIVE"
  }
]
```

---

## Cambios en Sprint 2 (retrocompatibles)

### `SpaceResponse` tiene dos campos nuevos

Aplica en `GET /spaces`, en `PATCH /spaces/{id}/status` y en `changedSpace` del evento SSE `occupancy`.

```json
{
  "id": 3,
  "code": "C-03",
  "vehicleType": "CAR",
  "status": "OCCUPIED",
  "currentPlate": "ABC123",
  "occupiedSince": "2026-10-14T15:30:00Z"
}
```

| Campo | Valor |
|---|---|
| `currentPlate` | Placa del vehículo registrado. `null` si la plaza está libre **o se ocupó a mano** con el `PATCH` |
| `occupiedSince` | Hora de ingreso de ese vehículo. `null` en los mismos casos |

### `PATCH /spaces/{id}/status`

| Código | `message` | Cuándo |
|---|---|---|
| 409 | `La plaza tiene un vehículo registrado; registra su salida` | Se intenta liberar (`AVAILABLE`) una plaza con un ingreso activo. Hay que usar `POST /entries/exit` |

Marcar `OCCUPIED` a mano sigue permitido (por ejemplo, un vehículo que entró sin registrarse); en ese caso
`currentPlate` y `occupiedSince` quedan en `null`.

### Barrera y alertas

Funcionan igual que en Sprint 2: si un ingreso ocupa la última plaza, el evento SSE trae `level: "FULL"` y
`entryBlocked: true`, y desde ese momento `POST /entries` responde 409 hasta que salga un vehículo.

---

## Tipos TypeScript sugeridos

```ts
export type VehicleType = 'CAR' | 'MOTORCYCLE';
export type EntryStatus = 'ACTIVE' | 'CLOSED';

export interface PlateRecognitionResponse {
  plate: string;
  confidence: number;
  lowConfidence: boolean;
  vehicleType: VehicleType | null;
  validFormat: boolean;
  alreadyInside: boolean;
}

export interface RegisterEntryRequest {
  plate: string;
  vehicleType?: VehicleType;
}

export interface RegisterExitRequest {
  plate: string;
}

export interface EntryResponse {
  id: number;
  plate: string;
  vehicleType: VehicleType;
  space: { id: number; code: string };
  entryTime: string;            // ISO-8601 UTC
  exitTime: string | null;
  durationMinutes: number | null;
  status: EntryStatus;
}

// Campos nuevos en SpaceResponse
export interface SpaceResponse {
  id: number;
  code: string;
  vehicleType: VehicleType;
  status: 'AVAILABLE' | 'OCCUPIED';
  currentPlate: string | null;
  occupiedSince: string | null;
}
```
