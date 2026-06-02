## Why

`WorldContextCollector.collectChests` tiene un check `isLoaded` que provoca que todos los cofres registrados se salten silenciosamente al construir el world_context. Además, `collectAnimals` solo captura animales pasivos (`Animal.class`), dejando fuera monstruos hostiles que son relevantes para Alfred (p. ej. zombies atacando al jugador).

## What Changes

- **Bug fix**: Eliminar `if (!level.isLoaded(pos)) continue;` en `collectChests` — la guardia es redundante (idéntico patrón al de `/butler chest inspect` que sí funciona) y provoca que el array `chests` llegue siempre vacío al backend.
- **Separar animales y monstruos**: `WorldContext.NearbyContext` pasa de un solo campo `animals` a dos campos independientes: `animals` (animales pasivos, `Animal.class`) y `monsters` (mobs hostiles, `Monster.class`).
- **Nuevo radio de escaneo**: el AABB cambia de esfera uniforme 30×30×30 a caja asimétrica **±25 bloques horizontales (X/Z) y ±5 bloques verticales (Y)** para ambos tipos de entidad.
- El JSON enviado al backend incluye `nearby.monsters` como campo nuevo; el backend lo recibe pero no lo utiliza hasta que se adapte en un cambio de backend independiente.

Sourceset afectado: **main** únicamente. Sin dependencias Gradle nuevas.

## Capabilities

### New Capabilities

- `world-context-mobs`: Escaneo de mobs hostiles cercanos y envío al backend como campo `nearby.monsters` en el world_context.

### Modified Capabilities

- `world-context-collector`: Cambia el requisito del collector — añade campo `monsters` a `NearbyContext`, corrige bug de cofres, y redefine el radio de escaneo de entidades.

## Impact

- **`src/main/.../context/WorldContext.java`**: añadir `monsters` a `NearbyContext`.
- **`src/main/.../context/WorldContextCollector.java`**: eliminar check `isLoaded`, añadir `collectMonsters`, ajustar AABB, actualizar `collect()`.
- **`src/main/.../http/ButlerHttpClient.java`**: añadir serialización de `monsters` en `worldContextToJson`.
- Backend: recibe el nuevo campo `nearby.monsters` (array de `{type, count}`) pero lo ignora hasta cambio de backend.
