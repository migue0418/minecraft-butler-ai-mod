## Why

El mod de Minecraft permite hablar con Alfred únicamente mediante el comando de texto `/butler ask <mensaje>`, lo que rompe la inmersión y es poco natural durante el juego. El backend ya expone `POST /api/butler/ask-voice` (con transcripción Whisper) pero ningún cliente del juego lo utiliza todavía.

## What Changes

- Se añade un sistema de **push-to-talk** en el mod Fabric: el jugador mantiene pulsada una tecla configurable (por defecto `V`) para grabar audio del micrófono y, al soltarla, el audio se envía al backend para obtener las acciones de Alfred.
- Se añade `VoiceRecorder` — captura PCM con `javax.sound.sampled.TargetDataLine` y construye los bytes WAV en memoria.
- Se añade `VoiceKeyBinding` — registra la tecla client-side con la API de keybindings de Fabric y orquesta el ciclo press→record / release→send.
- Se amplía `ButlerHttpClient` con `sendVoiceAsync(byte[] wavBytes)` que construye un `multipart/form-data` manualmente con `java.net.http.HttpClient` (sin dependencias nuevas) y llama a `/api/butler/ask-voice`.
- Las `ButlerAction` recibidas se ejecutan con el `ButlerActionExecutor` existente, igual que `/butler ask`.

## Capabilities

### New Capabilities

- `minecraft-voice-push-to-talk`: Captura de audio en el mod Minecraft y envío al endpoint de voz del backend. Cubre el ciclo completo: key press → grabación → key release → upload WAV → recepción de acciones → ejecución en el juego.

### Modified Capabilities

*(ninguna — los requisitos del backend `voice-stt-input` no cambian)*

## Impact

- **Solo el mod Java/Fabric** (`C:\Users\migue\Documents\Proyectos\MinecraftButlerAI`): clases nuevas en el paquete `voice/`, modificación de `ButlerHttpClient`.
- **Backend FastAPI**: sin cambios. El endpoint `POST /api/butler/ask-voice` ya está implementado y es compatible.
- **Sin dependencias nuevas**: `javax.sound.sampled` es JDK built-in; `java.net.http.HttpClient` ya se usa en el mod.
- **Sin cambios de modelo de datos ni migraciones Alembic**.
