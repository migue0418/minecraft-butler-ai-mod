## Context

`WorldContextCollector` (main sourceset) construye un snapshot del mundo antes de cada petición al backend. Actualmente tiene dos problemas:

1. `collectChests` tiene un `isLoaded` check que hace que todos los cofres registrados se ignoren silenciosamente (devuelve siempre lista vacía). El comando `/butler chest inspect` usa el mismo patrón de acceso al nivel sin ese check y funciona correctamente.
2. `collectAnimals` usa `Animal.class` como filtro, excluyendo mobs hostiles (`Monster.class`). Ambas clases son subclases independientes de `Mob` y no se solapan.

El cambio afecta exclusivamente al sourceset **main** y toca tres archivos: `WorldContext.java`, `WorldContextCollector.java` y `ButlerHttpClient.java`.

## Goals / Non-Goals

**Goals:**
- Eliminar el check `isLoaded` en `collectChests` para que los cofres registrados aparezcan en el world_context.
- Añadir `collectMonsters` con `Monster.class` en un AABB ±25 X/Z, ±5 Y.
- Ajustar `collectAnimals` al mismo AABB ±25 X/Z, ±5 Y.
- Exponer `monsters` como campo nuevo en `WorldContext.NearbyContext` y en la serialización JSON del HTTP client.

**Non-Goals:**
- Adaptar el backend para usar `nearby.monsters` (cambio de backend independiente).
- Capturar otros tipos de entidad (NPCs, jugadores, golems).
- Persistir o cachear el snapshot de entidades.

## Decisions

### D1: Eliminar `isLoaded` en lugar de reemplazarlo

`getBlockEntity` devuelve `null` si el chunk no está cargado; el check `instanceof Container` ya maneja ese caso. Eliminar `isLoaded` iguala el comportamiento con el del comando `inspect`, que funciona. Alternativa descartada: reemplazar por `level.isChunkLoaded(pos)` — añade complejidad sin beneficio claro.

### D2: AABB asimétrico ±25 H / ±5 V

El jugador se mueve principalmente en horizontal; el rango vertical de 5 bloques evita detectar mobs en cuevas bajo el suelo o en capas superiores irrelevantes. Alternativa descartada: radio esférico uniforme — da falsos positivos en vertical.

### D3: Campos separados `animals` y `monsters` en `NearbyContext`

Permite al backend y al LLM distinguir semánticamente entre fauna pacífica y amenazas. Alternativa descartada: campo único `entities` con campo `hostile: bool` — menos legible para el LLM, requiere más prompt engineering.

### D4: Backend recibe `monsters` pero lo ignora hasta su propio cambio

El JSON del mod incluye el campo desde ya. El backend no falla si recibe campos desconocidos (FastAPI ignora extras por defecto). Esto desacopla el despliegue del mod del despliegue del backend.

## Risks / Trade-offs

- **Rendimiento**: `getEntitiesOfClass(Monster.class, box)` recorre entidades cargadas en el AABB. Con un área 50×10×50 bloques, el coste es despreciable en partidas normales de singleplayer.  
  → Sin mitigación necesaria; si fuera un servidor con alta densidad de mobs se podría limitar la lista, pero está fuera del scope actual.

- **`isLoaded` eliminado**: en teoría podría llamarse `getBlockEntity` en un chunk no cargado. En la práctica, el mundo solo llama a `collect` desde el hilo del servidor con el jugador activo (para `/butler ask`) o en singleplayer con el jugador cerca (para voz). El riesgo es mínimo y consistente con el patrón ya usado en los comandos de cofre.  
  → Aceptado.
