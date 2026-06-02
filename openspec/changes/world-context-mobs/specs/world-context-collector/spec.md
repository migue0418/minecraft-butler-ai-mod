## MODIFIED Requirements

### Requirement: Escaneo de cofres registrados
El sistema SHALL leer el inventario de todos los cofres registrados en `ChestRegistry` usando `level.getBlockEntity(pos)` sin verificar `level.isLoaded(pos)` previamente. Si `getBlockEntity` devuelve null o el bloque no implementa `Container`, el cofre se omite silenciosamente.

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
El sistema SHALL usar un AABB de **±25 bloques en X y Z, ±5 bloques en Y** para escanear tanto animales como mobs hostiles. El radio anterior (±30 en los tres ejes) queda reemplazado.

#### Scenario: Entidad dentro del nuevo radio
- **WHEN** hay una vaca a 24 bloques horizontales y 3 bloques verticales del jugador
- **THEN** aparece en `nearby.animals`

#### Scenario: Entidad fuera del radio horizontal
- **WHEN** hay una oveja a 26 bloques horizontales del jugador
- **THEN** NO aparece en `nearby.animals`

#### Scenario: Entidad dentro del radio horizontal pero fuera del vertical
- **WHEN** hay un zombie a 10 bloques horizontales pero a 6 bloques por debajo del jugador
- **THEN** NO aparece en `nearby.monsters`
