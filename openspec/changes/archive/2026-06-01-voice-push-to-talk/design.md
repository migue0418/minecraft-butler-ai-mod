## Context

El mod ya dispone de `ButlerHttpClient.sendAsync(String)` para texto y de `ButlerActionExecutor` para ejecutar las acciones devueltas. El backend expone `POST /api/butler/ask-voice` (multipart WAV → `List<ButlerAction>`). Falta el lado cliente del mod: captura de micrófono, gestión del push-to-talk y envío del audio.

El proyecto usa `loom.splitEnvironmentSourceSets()`, por lo que el código de UI/input pertenece al sourceset `client` (`src/client/`) y tiene acceso al código del sourceset `main`. `AIButlerClient` (ya existente) es el punto de entrada para registrar keybindings y tick events.

Entorno objetivo: **`runClient` / servidor integrado** (single-player o LAN). En ese contexto, el cliente y el servidor comparten JVM y se puede obtener el `MinecraftServer` vía `Minecraft.getInstance().getSingleplayerServer()`.

## Goals / Non-Goals

**Goals:**
- Push-to-talk: mantener `V` para grabar, soltar para enviar.
- Captura de audio con `javax.sound.sampled` (sin deps nuevas).
- Envío del WAV como `multipart/form-data` con `java.net.http.HttpClient`.
- Ejecución de las `ButlerAction` recibidas en el hilo del servidor (igual que `/butler ask`).
- Retroalimentación mínima en el chat del juego (grabando / procesando / error).

**Non-Goals:**
- Compatibilidad con servidores dedicados (requeriría un paquete de red Fabric; queda para el cambio de streaming).
- Detección automática de voz (VAD); el push-to-talk manual es el VAD.
- Configuración de la tecla en pantalla de opciones (puede añadirse después).
- TTS / respuesta de audio.

## Decisions

### D1 — Audio en sourceset `client`, HTTP en `main`
`VoiceRecorder` y `VoiceKeyBinding` van en `src/client/.../voice/`. `ButlerHttpClient.sendVoiceAsync()` va en `src/main/.../http/` (misma clase que `sendAsync`). El código cliente llama al cliente HTTP del main porque el sourceset client puede depender de main.

**Alternativa descartada:** todo en client. Rompería la separación ya establecida y dificultaría pruebas del cliente HTTP de forma aislada.

### D2 — Formato de audio: PCM 44100 Hz 16-bit mono → WAV en memoria
Se abre un `TargetDataLine` con `AudioFormat(44100, 16, 1, true, false)`. Al soltar la tecla se vuelca el buffer a un `ByteArrayOutputStream` y se envuelve con `AudioSystem.write(..., AudioFileFormat.Type.WAVE, ...)` para producir bytes WAV válidos. Whisper acepta cualquier WAV; este formato es universalmente soportado por los drivers de audio.

**Alternativa descartada:** capturar a fichero temporal. Introduce I/O de disco innecesaria; en memoria es más simple y más rápido.

### D3 — Multipart construido manualmente
`java.net.http.HttpClient` no incluye soporte nativo de multipart. Se construye con `ByteArrayOutputStream` + boundary UUID:
```
--<boundary>
Content-Disposition: form-data; name="audio"; filename="voice.wav"
Content-Type: audio/wav

<bytes WAV>
--<boundary>--
```
Total: ~25 líneas. Sin dependencias nuevas.

**Alternativa descartada:** añadir OkHttp o Apache HttpClient. Introduce ~2 MB de deps innecesarias para algo trivial de hacer a mano.

### D4 — Ejecución de acciones: obtener source desde servidor integrado
Cuando llegan las acciones, se obtiene el servidor y el jugador así:
```java
MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
ServerPlayer player = server.getPlayerList().getPlayers().get(0);
CommandSourceStack source = player.createCommandSourceStack();
server.execute(() -> actions.forEach(a -> ButlerActionExecutor.execute(a, source)));
```
Simple y suficiente para el caso de uso local.

**Alternativa descartada:** ClientPlayNetworking para enviar un paquete al servidor. Requiere registrar un canal de red custom, añade complejidad y no es necesario mientras sea local.

### D5 — Poll de tecla vía `ClientTickEvents.END_CLIENT_TICK`
Fabric no ofrece callbacks de "key pressed" / "key released" directos para keybindings. El patrón estándar es: en cada tick de cliente, comparar `keyBinding.isDown()` con un estado booleano anterior (`wasDown`) para detectar el flanco de bajada (inicio de grabación) y el de subida (fin de grabación + envío).

## Risks / Trade-offs

- **Driver de audio no disponible** → `ButlerHttpClient` ya maneja fallos async; el `VoiceRecorder` debe capturar `LineUnavailableException` y notificar en el chat en lugar de crashear el juego.
- **Grabación muy larga (usuario olvida soltar)** → Límite de 30s: si el buffer supera ese tiempo de audio, se envía automáticamente y se muestra aviso.
- **Hilo de grabación vs hilo de juego** → `TargetDataLine.read()` bloquea; se lanza en un `Thread` virtual (Java 25 ya disponible en el proyecto). El join / stop es seguro porque `TargetDataLine.stop()` desbloquea el read.
- **Solo funciona en servidor integrado** → Documentado como non-goal; se abordará en el cambio de streaming con networking layer.

## Migration Plan

1. Crear rama `feature/voice-push-to-talk`.
2. Añadir `VoiceRecorder`, `VoiceKeyBinding` en `src/client/.../voice/`.
3. Añadir `sendVoiceAsync` en `ButlerHttpClient` (src/main).
4. Registrar key binding y tick event en `AIButlerClient.onInitializeClient()`.
5. Verificar con `./gradlew.bat runClient`: pulsar `V`, hablar, soltar, ver respuesta en chat.

No hay migración de datos ni cambios en el backend. Rollback: borrar las clases nuevas y revertir `AIButlerClient`.

## Open Questions

- *(ninguna pendiente — alcance claro para el caso local)*
