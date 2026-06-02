## Why

Alfred responde preguntas sobre Minecraft pero no sabe nada del mundo concreto del jugador: no conoce el contenido de sus cofres, qué animales tiene cerca ni el estado de sus cultivos. Añadir un snapshot del mundo en cada petición permite que el LLM del backend tome decisiones informadas sobre el estado real de la partida.

## What Changes

- Se añade `WorldContext` — record Java que encapsula inventario del jugador, cofres registrados (leídos en tiempo real desde sus `BlockEntity`), animales cercanos y cultivos cercanos.
- Se añade `WorldContextCollector` — clase server-side que construye el `WorldContext` en el momento de cada petición.
- Se amplía el payload JSON de `ButlerHttpClient` para incluir el campo `world_context` junto al mensaje de texto o de voz.
- Los puntos de entrada existentes (`/butler ask` y el keybinding de voz) construyen el contexto antes de llamar al HTTP client.

## Capabilities

### New Capabilities

- `minecraft-world-context`: El mod captura y envía al backend un snapshot del estado del mundo en el momento de cada petición al butler: inventario del jugador, contenido actual de cofres registrados (lectura en tiempo real del BlockEntity), entidades animales en radio 30 bloques y bloques de cultivo en radio 20 bloques.

### Modified Capabilities

*(ninguna — el contrato de la capability `minecraft-voice-push-to-talk` no cambia; el payload HTTP es aditivo)*

## Impact

- **Sourceset `main`**: nuevas clases `WorldContext` y `WorldContextCollector`; modificación de `ButlerHttpClient` (serialización del contexto) y `ButlerCommand` (construir contexto en `/butler ask`).
- **Sourceset `client`**: modificación de `VoiceKeyBinding` (construir contexto vía `getSingleplayerServer()` antes de enviar audio).
- **Sin dependencias nuevas**: usa Minecraft API (ya disponible), Gson (ya presente) y `ChestRegistry` existente.
- **Backend**: el campo `world_context` llegará como JSON adicional en el body. El backend lo ignorará inicialmente hasta que se adapte en una fase posterior separada.
