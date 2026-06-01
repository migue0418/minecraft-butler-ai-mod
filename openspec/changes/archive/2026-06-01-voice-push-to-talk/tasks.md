## 0. Setup (OBLIGATORIO - PRIMER PASO)

- [x] 0.1 Leer el plan técnico en `C:\Users\migue\Documents\Proyectos\MinecraftButlerAI\.claude\doc\voice-push-to-talk\mod.md` antes de tocar código
- [x] 0.2 Crear rama `feature/voice-push-to-talk` en el repositorio del mod (`C:\Users\migue\Documents\Proyectos\MinecraftButlerAI`)

## 1. HTTP — `sendVoiceAsync` en `ButlerHttpClient` (main sourceset)

- [x] 1.1 Añadir método `sendVoiceAsync(byte[] wavBytes)` en `ButlerHttpClient.java` que construya el multipart/form-data manualmente (boundary UUID, parte `audio` con `filename="voice.wav"`) y llame a `POST /api/butler/ask-voice` con el token JWT en cache (mismo patrón de retry que `sendAsync`)
- [x] 1.2 Reusar el helper de parsing de `List<ButlerAction>` ya existente en `askAsync` — extraer si es necesario para no duplicar

## 2. Audio — `VoiceRecorder` (client sourceset)

- [x] 2.1 Crear `src/client/.../voice/VoiceRecorder.java`: abre `TargetDataLine` con `AudioFormat(44100f, 16, 1, true, false)`, escribe en `ByteArrayOutputStream` en un hilo virtual (`Thread.ofVirtual().start(...)`)
- [x] 2.2 Añadir `start()`: abre la línea y lanza el hilo de captura. Lanzar excepción si ya hay grabación en curso.
- [x] 2.3 Añadir `stop(): byte[]`: detiene la línea, espera al hilo, envuelve el PCM con `AudioSystem.write(..., AudioFileFormat.Type.WAVE, ...)` y devuelve los bytes WAV
- [x] 2.4 Añadir lógica de límite: si el buffer supera 30s de audio (44100 × 2 bytes/muestra × 30 = ~2.6 MB), detener automáticamente y notificar al callback
- [x] 2.5 Capturar `LineUnavailableException` en `start()` y exponer como resultado de error (no lanzar al hilo del juego)

## 3. Input — `VoiceKeyBinding` (client sourceset)

- [x] 3.1 Crear `src/client/.../voice/VoiceKeyBinding.java`: declara el `KeyBinding` (`Butler: Push to Talk`, `GLFW.GLFW_KEY_V`, categoría `key.categories.gameplay`)
- [x] 3.2 Registrar con `KeyMappingHelper.registerKeyMapping(...)` en un método `static register()` (API renombrada en Fabric API 0.149)
- [x] 3.3 Registrar `ClientTickEvents.END_CLIENT_TICK` para polling: mantener `boolean wasDown`. En flanco descendente (false→true) llamar a `onPress()`; en flanco ascendente (true→false) llamar a `onRelease()`
- [x] 3.4 `onPress()`: si hay pantalla abierta (`mc.screen != null`) ignorar; si audio < 300ms ignorar en `onRelease`; iniciar `VoiceRecorder.start()`; mostrar `[Alfred] Grabando...` en el chat del cliente
- [x] 3.5 `onRelease()`: llamar a `VoiceRecorder.stop()` → validar duración mínima (300ms = ~26.5 KB PCM raw) → si OK llamar a `ButlerHttpClient.sendVoiceAsync(bytes).thenAccept(...)` → ejecutar acciones en servidor integrado vía `Minecraft.getInstance().getSingleplayerServer().execute(...)`; mostrar `[Alfred] No pude entenderte. Intenta de nuevo.` si el backend responde 422

## 4. Integración — `AIButlerClient`

- [x] 4.1 En `AIButlerClient.onInitializeClient()` añadir: `VoiceKeyBinding.register()`

## 5. Verificación (OBLIGATORIO - EL AGENTE LO EJECUTA)

- [x] 5.1 Compilar: `cd C:\Users\migue\Documents\Proyectos\MinecraftButlerAI && .\gradlew.bat compileJava` — BUILD SUCCESSFUL (main + client, `--offline` requerido por SNAPSHOT de Loom)
- [x] 5.2 Arrancar el backend FastAPI: `cd <backend> && uv run uvicorn app.main:app --reload`
- [x] 5.3 Lanzar el cliente: `.\gradlew.bat runClient --offline` — verificar en los logs que no hay errores de inicialización del keybinding
- [x] 5.4 En el juego: abrir un mundo, pulsar `V`, hablar ~2s, soltar — verificar que aparece `[Alfred] Grabando...` al pulsar y la respuesta de Alfred al soltar
- [x] 5.5 Verificar escenario de error: pulsar y soltar `V` muy rápido — debe aparecer `[Alfred] Audio demasiado corto.`
- [x] 5.6 Verificar que `V` no activa la grabación con el inventario abierto (`E`)

## 6. Cierre (OBLIGATORIO)

- [x] 6.1 Actualizar `C:\Users\migue\Documents\Proyectos\MinecraftButlerAI\CLAUDE.md`: mover `Voice input` de "Do not implement yet" y documentar las nuevas clases y API adaptations
- [x] 6.2 Abrir PR en el repositorio del mod con `gh pr create` — PR #1 creado
