## Requirements

### Requirement: Snapshot del mundo adjunto a cada petición al butler
El mod SHALL construir un snapshot del estado del mundo en el momento de cada petición a `/api/butler/ask` o `/api/butler/ask-voice` y adjuntarlo como campo `world_context` en el payload JSON. El snapshot SHALL incluir inventario del jugador, contenidos actuales de todos los cofres registrados, animales cercanos agrupados por tipo, mobs hostiles cercanos agrupados por tipo y cultivos cercanos agrupados por tipo y estado de madurez.

#### Scenario: Petición de texto con contexto completo
- **WHEN** el jugador ejecuta `/butler ask <mensaje>` con cofres registrados cargados y animales/cultivos en el radio de escaneo
- **THEN** el JSON enviado al backend incluye `world_context` con `player.inventory`, `chests`, `nearby.animals`, `nearby.monsters` y `nearby.crops` rellenos con los datos actuales del mundo

#### Scenario: Petición de voz con contexto completo
- **WHEN** el jugador suelta la tecla push-to-talk en una partida local con el mundo cargado
- **THEN** el payload multipart de `/ask-voice` lleva también el snapshot adjunto en el mismo JSON que se envía

#### Scenario: Sin cofres registrados
- **WHEN** el jugador hace una petición y no hay cofres en el `ChestRegistry`
- **THEN** el campo `chests` del contexto es una lista vacía y la petición se envía igualmente

#### Scenario: Sin animales ni cultivos en el radio
- **WHEN** el jugador está en un área sin animales dentro del radio de escaneo y sin cultivos dentro de 20 bloques
- **THEN** `nearby.animals`, `nearby.monsters` y `nearby.crops` son listas vacías; el contexto se envía igualmente

### Requirement: Inventario del jugador en el contexto
El snapshot SHALL incluir todos los ítems no vacíos del inventario del jugador (slots de hotbar, inventario principal y armadura), identificados por su ID de recurso (`namespace:id`) y cantidad total por tipo.

#### Scenario: Jugador con ítems variados
- **WHEN** el jugador tiene 18 trigos en el inventario y 3 espadas de piedra
- **THEN** `player.inventory` contiene `[{"item": "minecraft:wheat", "count": 18}, {"item": "minecraft:stone_sword", "count": 3}]`

#### Scenario: Inventario vacío
- **WHEN** el jugador no tiene ningún ítem
- **THEN** `player.inventory` es una lista vacía

### Requirement: Escaneo de cofres registrados
El mod SHALL leer el inventario de todos los cofres registrados en `ChestRegistry` usando `level.getBlockEntity(pos)` sin verificar `level.isLoaded(pos)` previamente. Si `getBlockEntity` devuelve null o el bloque no implementa `Container`, el cofre se omite silenciosamente.

#### Scenario: Cofres con items en el overworld
- **WHEN** el jugador ejecuta `/butler ask` y hay cofres registrados con items en el overworld cargado
- **THEN** `world_context.chests` contiene una entrada por cofre con su nombre y la lista de items

#### Scenario: Cofres físicamente vacíos
- **WHEN** un cofre registrado existe pero no tiene items
- **THEN** el cofre aparece en `world_context.chests` con `"items": []`

#### Scenario: Posición de cofre ya no contiene un chest
- **WHEN** el bloque en la posición registrada no es un cofre (o el chunk no está cargado)
- **THEN** ese cofre se omite de `world_context.chests` sin error

### Requirement: Radio de escaneo de entidades cercanas
El mod SHALL usar un AABB de **±25 bloques en X y Z, ±5 bloques en Y** para escanear tanto animales como mobs hostiles.

#### Scenario: Entidad dentro del nuevo radio
- **WHEN** hay una vaca a 24 bloques horizontales y 3 bloques verticales del jugador
- **THEN** aparece en `nearby.animals`

#### Scenario: Entidad fuera del radio horizontal
- **WHEN** hay una oveja a 26 bloques horizontales del jugador
- **THEN** NO aparece en `nearby.animals`

#### Scenario: Entidad dentro del radio horizontal pero fuera del vertical
- **WHEN** hay un zombie a 10 bloques horizontales pero a 6 bloques por debajo del jugador
- **THEN** NO aparece en `nearby.monsters`

### Requirement: Animales cercanos agrupados por tipo
El mod SHALL escanear las entidades de tipo `Animal` en el AABB de escaneo (±25 X/Z, ±5 Y) alrededor del jugador y agruparlas por tipo de entidad.

#### Scenario: Vacas y pollos cerca del jugador
- **WHEN** hay 4 vacas y 7 pollos dentro del radio de escaneo del jugador
- **THEN** `nearby.animals` contiene `[{"type": "minecraft:cow", "count": 4}, {"type": "minecraft:chicken", "count": 7}]`

#### Scenario: Radio sin animales
- **WHEN** no hay animales dentro del radio de escaneo
- **THEN** `nearby.animals` es una lista vacía

### Requirement: Cultivos cercanos por tipo y madurez
El mod SHALL escanear los bloques del tipo `CropBlock` en un radio de 20 bloques (cubo, ±10 X/Z, ±5 Y) alrededor del jugador y reportar por cada tipo de cultivo cuántos están maduros (`age == maxAge`) y cuántos aún están creciendo.

#### Scenario: Trigo maduro y zanahorias en crecimiento
- **WHEN** hay 9 bloques de trigo en edad máxima y 3 zanahorias en crecimiento dentro de 20 bloques
- **THEN** `nearby.crops` contiene `[{"type": "minecraft:wheat", "mature": 9, "growing": 3}, {"type": "minecraft:carrots", "mature": 0, "growing": 3}]`

#### Scenario: Radio sin cultivos
- **WHEN** no hay cultivos dentro de 20 bloques
- **THEN** `nearby.crops` es una lista vacía
