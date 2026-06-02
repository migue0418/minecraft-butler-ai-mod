## ADDED Requirements

### Requirement: Snapshot del mundo adjunto a cada petición al butler
El mod SHALL construir un snapshot del estado del mundo en el momento de cada petición a `/api/butler/ask` o `/api/butler/ask-voice` y adjuntarlo como campo `world_context` en el payload JSON. El snapshot SHALL incluir inventario del jugador, contenidos actuales de todos los cofres registrados, animales cercanos agrupados por tipo y cultivos cercanos agrupados por tipo y estado de madurez.

#### Scenario: Petición de texto con contexto completo
- **WHEN** el jugador ejecuta `/butler ask <mensaje>` con cofres registrados cargados y animales/cultivos en el radio de escaneo
- **THEN** el JSON enviado al backend incluye `world_context` con `player.inventory`, `chests`, `nearby.animals` y `nearby.crops` rellenos con los datos actuales del mundo

#### Scenario: Petición de voz con contexto completo
- **WHEN** el jugador suelta la tecla push-to-talk en una partida local con el mundo cargado
- **THEN** el payload multipart de `/ask-voice` lleva también el snapshot adjunto en el mismo JSON que se envía

#### Scenario: Sin cofres registrados
- **WHEN** el jugador hace una petición y no hay cofres en el `ChestRegistry`
- **THEN** el campo `chests` del contexto es una lista vacía y la petición se envía igualmente

#### Scenario: Sin animales ni cultivos en el radio
- **WHEN** el jugador está en un área sin animales dentro de 30 bloques y sin cultivos dentro de 20 bloques
- **THEN** `nearby.animals` y `nearby.crops` son listas vacías; el contexto se envía igualmente

### Requirement: Inventario del jugador en el contexto
El snapshot SHALL incluir todos los ítems no vacíos del inventario del jugador (slots de hotbar, inventario principal y armadura), identificados por su ID de recurso (`namespace:id`) y cantidad total por tipo.

#### Scenario: Jugador con ítems variados
- **WHEN** el jugador tiene 18 trigos en el inventario y 3 espadas de piedra
- **THEN** `player.inventory` contiene `[{"item": "minecraft:wheat", "count": 18}, {"item": "minecraft:stone_sword", "count": 3}]`

#### Scenario: Inventario vacío
- **WHEN** el jugador no tiene ningún ítem
- **THEN** `player.inventory` es una lista vacía

### Requirement: Contenido actual de cofres registrados
El mod SHALL leer el contenido de cada cofre registrado en `ChestRegistry` directamente desde su `BlockEntity` en el momento de la petición, agrupando ítems por tipo. Los cofres cuyos chunks no estén cargados o cuyo bloque ya no sea un contenedor SHALL omitirse silenciosamente.

#### Scenario: Cofre registrado cargado con ítems
- **WHEN** el cofre "despensa" está registrado y su chunk está cargado, con 47 trigos y 12 semillas
- **THEN** `chests` incluye `{"name": "despensa", "items": [{"item": "minecraft:wheat", "count": 47}, {"item": "minecraft:wheat_seeds", "count": 12}]}`

#### Scenario: Cofre registrado en chunk no cargado
- **WHEN** un cofre está registrado pero su chunk no está cargado en el momento de la petición
- **THEN** ese cofre se omite del snapshot sin error; el resto de cofres cargados se incluyen normalmente

#### Scenario: Bloque ya no es un cofre
- **WHEN** un cofre registrado ha sido eliminado o reemplazado por otro bloque
- **THEN** ese cofre se omite del snapshot sin error

### Requirement: Animales cercanos agrupados por tipo
El mod SHALL escanear las entidades de tipo `Animal` en un radio de 30 bloques (AABB cúbica) alrededor del jugador y agruparlas por tipo de entidad.

#### Scenario: Vacas y pollos cerca del jugador
- **WHEN** hay 4 vacas y 7 pollos dentro de 30 bloques del jugador
- **THEN** `nearby.animals` contiene `[{"type": "minecraft:cow", "count": 4}, {"type": "minecraft:chicken", "count": 7}]`

#### Scenario: Radio sin animales
- **WHEN** no hay animales dentro de 30 bloques
- **THEN** `nearby.animals` es una lista vacía

### Requirement: Cultivos cercanos por tipo y madurez
El mod SHALL escanear los bloques del tipo `CropBlock` en un radio de 20 bloques (cubo) alrededor del jugador y reportar por cada tipo de cultivo cuántos están maduros (`age == maxAge`) y cuántos aún están creciendo.

#### Scenario: Trigo maduro y zanahorias en crecimiento
- **WHEN** hay 9 bloques de trigo en edad máxima y 3 zanahorias en crecimiento dentro de 20 bloques
- **THEN** `nearby.crops` contiene `[{"type": "minecraft:wheat", "mature": 9, "growing": 3}, {"type": "minecraft:carrots", "mature": 0, "growing": 3}]`

#### Scenario: Radio sin cultivos
- **WHEN** no hay cultivos dentro de 20 bloques
- **THEN** `nearby.crops` es una lista vacía
