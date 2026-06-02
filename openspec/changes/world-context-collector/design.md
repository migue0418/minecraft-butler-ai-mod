## Context

El mod envía a `/api/butler/ask` y `/api/butler/ask-voice` únicamente el texto del mensaje. El backend (LangGraph + LLM) no dispone de ninguna información sobre el estado real del mundo del jugador. Este cambio añade un snapshot del mundo al payload de cada petición, sin alterar el contrato existente de respuesta (sigue siendo `List<ButlerAction>`).

El mod ya dispone de `ChestRegistry` (posiciones y filtros de cofres persistentes) y de la lógica de lectura de `BlockEntity` en `ButlerCommand` (`/butler chest inspect`). El collector reutilizará ese patrón. `ButlerHttpClient` ya construye JSON con Gson; el contexto se añadirá como campo adicional.

## Goals / Non-Goals

**Goals:**
- Construir un `WorldContext` en el momento de cada petición: inventario del jugador, contenido actual de cofres registrados (lectura real del `BlockEntity`), animales en radio 30 bloques (tipo + cantidad), cultivos en radio 20 bloques (tipo + maduros/creciendo).
- Serializar el contexto como `world_context` en el JSON enviado al backend.
- Integrar la recolección en `/butler ask` (server-side) y en el keybinding de voz (client-side vía `getSingleplayerServer()`).

**Non-Goals:**
- Adaptar el backend para consumir `world_context` (fase posterior separada).
- Escanear bloques que no sean cultivos conocidos.
- Inventario de entidades (mobs, jugadores remotos).
- Datos de bioma, luz, estructuras.
- Persistencia del contexto (snapshot efímero, solo en RAM durante la petición).

## Decisions

### D1 — `WorldContext` como record Java plano

Se define un record `WorldContext` con campos tipados para cada categoría de datos. La serialización a JSON la realiza Gson (ya presente). El record se sitúa en `src/main/.../context/` porque tanto el comando server-side como el cliente de voz necesitan acceder a él.

**Alternativa descartada:** `Map<String, Object>` genérico. Reduce la seguridad de tipos sin ventaja real.

### D2 — `WorldContextCollector` server-side, llamado desde cliente vía `getSingleplayerServer()`

La recolección requiere acceso al `MinecraftServer` (para leer `BlockEntity` y entidades). En `/butler ask` se ejecuta en el hilo del servidor directamente. En `VoiceKeyBinding` (client sourceset) se obtiene el servidor integrado con `Minecraft.getInstance().getSingleplayerServer()`, igual que el patrón ya establecido para ejecutar acciones tras la respuesta de voz.

**Alternativa descartada:** recolección en el cliente sin servidor. No tiene acceso a `BlockEntity` ni a la lista completa de entidades del mundo.

### D3 — Escaneo síncrono en el hilo de la petición

El collector se ejecuta en el mismo hilo que invoca al HTTP client (hilo del servidor para comandos, hilo virtual para voz). El escaneo es breve (<1 ms para los radios definidos) y no justifica complejidad asíncrona adicional.

**Alternativa descartada:** snapshot en background/ticker. Introduce datos potencialmente stale y complejidad de sincronización.

### D4 — Cofres: lectura en tiempo real del `BlockEntity`

Cada cofre registrado se lee desde su `BlockEntity` en el momento de la petición. Si el chunk no está cargado o el bloque ya no es un contenedor, se omite ese cofre (sin error). Esto garantiza que Alfred ve el estado actual, no un caché.

**Alternativa descartada:** cachear contenidos en `ChestRegistry`. Los cofres cambian frecuentemente; un caché stale sería peor que no tener datos.

### D5 — Animales: query de entidades en el servidor

Se usa `ServerLevel.getEntitiesOfClass(Animal.class, AABB)` con un AABB de radio 30 bloques centrado en el jugador. Se agrupa por tipo (`EntityType`) y se cuenta. No se envían posiciones individuales de entidades para mantener el payload pequeño.

### D6 — Cultivos: scan de bloques en radio 20

Se itera el cubo de bloques centrado en el jugador (radio 20, altura ±5). Para cada bloque que sea `CropBlock` (base de todos los cultivos vanilla) se registra tipo y si está maduro (`age == maxAge`). Se omiten bloques en chunks no cargados.

## Risks / Trade-offs

- **Cofres en chunks no cargados** → el collector los omite silenciosamente; el LLM verá menos cofres de los registrados. Aceptable para MVP.
- **Payload más grande** → un mundo con 10 cofres registrados y un radio poblado puede añadir ~2-5 KB al request. Negligible para HTTP local.
- **Hilo del servidor bloqueado** → el escaneo de bloques en radio 20 itera ~40k posiciones en el peor caso. En la práctica, la mayoría de chunks contienen pocos cultivos y el scan se interrumpe en bloques no cargados. Si en el futuro resulta lento, se puede limitar a chunks cargados o reducir el radio.
- **Solo funciona en servidor integrado** (singleplayer/LAN) para la ruta de voz — mismo non-goal que en `voice-push-to-talk`.

## Migration Plan

1. Crear rama `feature/world-context-collector`.
2. Añadir `WorldContext` record y `WorldContextCollector` en `src/main/.../context/`.
3. Modificar `ButlerHttpClient` para aceptar y serializar `WorldContext`.
4. Modificar `ButlerCommand` para construir el contexto en `/butler ask`.
5. Modificar `VoiceKeyBinding` para construir el contexto antes del envío.
6. Compilar y verificar en `runClient`. El backend recibe el campo extra y lo ignora hasta su adaptación.

Rollback: revertir los cambios en `ButlerHttpClient`, `ButlerCommand` y `VoiceKeyBinding`; eliminar el paquete `context/`. Sin migración de datos.

## Open Questions

*(ninguna — alcance claro para MVP reactivo)*
