## ADDED Requirements

### Requirement: Escaneo de mobs hostiles cercanos
El sistema SHALL escanear mobs hostiles (`Monster.class`) en un AABB de ±25 bloques en X/Z y ±5 bloques en Y alrededor del jugador, agrupados por tipo de entidad, y añadirlos al world_context como `nearby.monsters`.

#### Scenario: Mobs hostiles detectados
- **WHEN** el jugador tiene mobs hostiles dentro del radio (±25 X/Z, ±5 Y)
- **THEN** `nearby.monsters` contiene una entrada por tipo con su identificador (`minecraft:zombie`, `minecraft:spider`, etc.) y el conteo total

#### Scenario: Sin mobs hostiles en el radio
- **WHEN** no hay mobs hostiles dentro del radio de escaneo
- **THEN** `nearby.monsters` es una lista vacía `[]`

#### Scenario: Mobs fuera del radio vertical
- **WHEN** hay un mob hostil directamente debajo del jugador a más de 5 bloques de distancia vertical
- **THEN** ese mob NO aparece en `nearby.monsters`

### Requirement: Separación semántica animales y monstruos en el JSON
El sistema SHALL enviar `nearby.animals` y `nearby.monsters` como campos independientes en el objeto `nearby` del JSON de world_context.

#### Scenario: JSON con ambos campos presentes
- **WHEN** el world_context se serializa
- **THEN** el JSON contiene `"nearby": { "animals": [...], "monsters": [...], "crops": [...] }`

#### Scenario: Backend recibe campo desconocido sin error
- **WHEN** el backend recibe el campo `nearby.monsters` que aún no procesa
- **THEN** el backend no falla (FastAPI ignora campos extra en el body)
