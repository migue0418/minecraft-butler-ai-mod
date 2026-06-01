## ADDED Requirements

### Requirement: Push-to-talk para enviar voz al butler
El mod SHALL capturar audio del micrófono del sistema mientras el jugador mantiene pulsada la tecla push-to-talk (por defecto `V`). Al soltar la tecla, el audio SHALL enviarse como WAV al endpoint `POST /api/butler/ask-voice` del backend. Las `ButlerAction` recibidas SHALL ejecutarse en el hilo del servidor igual que las del comando de texto `/butler ask`.

#### Scenario: Happy path — hablar y recibir respuesta
- **WHEN** el jugador mantiene pulsada la tecla `V`, habla y la suelta
- **THEN** el chat muestra `[Alfred] Procesando...`, y a continuación aparecen los mensajes de las `ButlerAction` devueltas por el backend

#### Scenario: Retroalimentación visual al empezar a grabar
- **WHEN** el jugador pulsa la tecla `V` por primera vez
- **THEN** aparece un mensaje en el chat `[Alfred] 🎤 Grabando...` (o equivalente sin emoji si el chat no los soporta)

#### Scenario: Audio demasiado corto o vacío
- **WHEN** el jugador pulsa y suelta la tecla `V` en menos de 300 ms (sin hablar)
- **THEN** no se envía ninguna petición al backend y el chat muestra `[Alfred] Audio demasiado corto.`

#### Scenario: Backend no disponible
- **WHEN** el jugador envía audio pero el backend no está corriendo
- **THEN** el chat muestra `[Alfred] No pude contactar con el servidor.` (mismo mensaje que `/butler ask` en ese caso)

#### Scenario: Error de transcripción (422 del backend)
- **WHEN** el backend responde 422 (audio vacío o no transcribible)
- **THEN** el chat muestra `[Alfred] No pude entenderte. Intenta de nuevo.`

### Requirement: Límite de duración de grabación
El mod SHALL detener la grabación automáticamente si supera 30 segundos, enviar el audio acumulado y notificar al jugador, para evitar que una tecla atascada sature la memoria.

#### Scenario: Grabación cortada por límite de tiempo
- **WHEN** el jugador mantiene la tecla `V` pulsada durante más de 30 segundos
- **THEN** la grabación se detiene, el audio se envía automáticamente y el chat muestra `[Alfred] Grabación máxima alcanzada, enviando...`

### Requirement: Tecla push-to-talk registrada como Fabric KeyBinding
La tecla SHALL registrarse con la API de keybindings de Fabric (`KeyBindingHelper`) para que aparezca en la pantalla de controles del juego y sea configurable por el jugador.

#### Scenario: Tecla visible en controles
- **WHEN** el jugador abre `Options → Controls → Key Binds`
- **THEN** aparece la entrada `Butler: Push to Talk` con la tecla asignada (`V` por defecto)

#### Scenario: Tecla no funciona en pantallas de UI
- **WHEN** el jugador tiene abierto un inventario, un cofre o cualquier pantalla de Minecraft
- **THEN** la tecla `V` no activa la grabación (Fabric KeyBinding ignora la pulsación cuando hay una pantalla activa)
