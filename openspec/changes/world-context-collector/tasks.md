## 0. Setup (OBLIGATORIO - PRIMER PASO)

- [x] 0.1 Leer el plan técnico en `.claude/doc/world-context-collector/mod.md` antes de tocar código
- [x] 0.2 Crear rama `feature/world-context-collector` en el repositorio del mod

## 1. Main sourceset: paquete `context/`

- [x] 1.1 Crear `src/main/java/com/miguealguacil/butler/context/WorldContext.java`: record con campos `PlayerContext player`, `List<ChestContext> chests`, `NearbyContext nearby`; incluir sub-records `PlayerContext(List<ItemStack> inventory, int x, int y, int z)`, `ChestContext(String name, List<ItemEntry> items)`, `NearbyContext(List<AnimalGroup> animals, List<CropGroup> crops)`, `ItemEntry(String item, int count)`, `AnimalGroup(String type, int count)`, `CropGroup(String type, int mature, int growing)`
- [x] 1.2 Crear `src/main/java/com/miguealguacil/butler/context/WorldContextCollector.java` con método estático `collect(ServerPlayer player, MinecraftServer server): WorldContext` que orquesta los cuatro escaneos
- [x] 1.3 Implementar `collectInventory(ServerPlayer): List<ItemEntry>`: iterar slots de inventario principal + armadura, agrupar por `ResourceLocation` del ítem, omitir ítems vacíos
- [x] 1.4 Implementar `collectChests(MinecraftServer): List<ChestContext>`: iterar `ChestRegistry.getAll()`, resolver el `ServerLevel` por dimensión, obtener el `BlockEntity` de la posición, si implementa `Container` iterar sus slots y agrupar por tipo; omitir silenciosamente si chunk no cargado o bloque no es contenedor
- [x] 1.5 Implementar `collectAnimals(ServerPlayer, ServerLevel): List<AnimalGroup>`: usar `ServerLevel.getEntitiesOfClass(Animal.class, AABB centrada en jugador, radio 30)`, agrupar por `EntityType.getKey(entity.getType()).toString()`, devolver lista ordenada por count descendente
- [x] 1.6 Implementar `collectCrops(ServerPlayer, ServerLevel): List<CropGroup>`: iterar posiciones en cubo radio 20 bloques alrededor del jugador (skip si chunk no cargado), si el bloque es `instanceof CropBlock` obtener `age` y `getMaxAge()`, agrupar por tipo de bloque

## 2. Main sourceset: `ButlerHttpClient`

- [x] 2.1 Añadir serialización de `WorldContext` a `JsonObject` en un método privado `worldContextToJson(WorldContext): JsonElement` usando Gson o construcción manual con `JsonObject`/`JsonArray`
- [x] 2.2 Modificar `sendAsync(String message)` → `sendAsync(String message, WorldContext context)`: añadir `world_context` al body JSON si `context != null`
- [x] 2.3 Modificar `sendVoiceAsync(byte[] wavBytes)` → `sendVoiceAsync(byte[] wavBytes, WorldContext context)`: añadir campo `world_context` serializado como parte adicional del multipart, o como campo JSON separado según lo que soporte el backend en su fase de adaptación — por ahora añadirlo como header/form field adicional de tipo texto

## 3. Main sourceset: `ButlerCommand`

- [x] 3.1 En el handler de `/butler ask <mensaje>`: llamar a `WorldContextCollector.collect(player, server)` antes de `ButlerHttpClient.sendAsync(...)` y pasar el contexto como segundo argumento

## 4. Client sourceset: `VoiceKeyBinding`

- [x] 4.1 En `doSend(Minecraft mc)`: antes de llamar a `ButlerHttpClient.sendVoiceAsync(wav)`, obtener `MinecraftServer server = mc.getSingleplayerServer()` y `ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null)`; si `player != null` construir `WorldContextCollector.collect(player, server)` y pasarlo a `sendVoiceAsync`; si `player == null` pasar `null` (el contexto se omite)

## 5. Compilación (OBLIGATORIO)

- [x] 5.1 `.\gradlew.bat compileJava --offline` → BUILD SUCCESSFUL para main y client; guardar resultado en `openspec/changes/world-context-collector/reports/YYYY-MM-DD-build.md`

## 6. Prueba en el juego (OBLIGATORIO - EL AGENTE LO EJECUTA)

- [ ] 6.1 Arrancar backend FastAPI: `cd <backend> && uv run uvicorn app.main:app --reload`
- [ ] 6.2 Lanzar cliente: `.\gradlew.bat runClient --offline` — verificar en logs que no hay errores de inicialización
- [ ] 6.3 Registrar un cofre con `/butler chest register despensa`, meter ítems, ejecutar `/butler ask ¿qué hay en mis cofres?` — verificar en los logs del backend que el JSON recibido incluye el campo `world_context` con los datos del cofre
- [ ] 6.4 Hablar por voz con `V`: mencionar animales o cultivos — verificar en los logs del backend que el campo `world_context` llega también en la petición de voz
- [ ] 6.5 Documentar resultados en `openspec/changes/world-context-collector/reports/YYYY-MM-DD-test.md`

## 7. Cierre (OBLIGATORIO)

- [x] 7.1 Actualizar `CLAUDE.md`: documentar el nuevo paquete `context/` y la capability `world-context-collector`
- [ ] 7.2 PR con `gh pr create` usando la skill `write-pr-report`
