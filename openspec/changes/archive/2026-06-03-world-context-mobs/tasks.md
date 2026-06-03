## 0. Setup (OBLIGATORIO - PRIMER PASO)

- [x] 0.1 Leer plan técnico en `.claude/doc/world-context-mobs/mod.md` (generarlo con agente mod-developer si no existe)
- [x] 0.2 Crear rama `feature/world-context-mobs`

## 1. Main sourceset: context

- [x] 1.1 `WorldContext.java` — añadir campo `monsters` a `NearbyContext` record: `List<AnimalGroup> monsters`
- [x] 1.2 `WorldContextCollector.java` — eliminar `if (!level.isLoaded(pos)) continue;` en `collectChests` (línea ~70)
- [x] 1.3 `WorldContextCollector.java` — añadir método `collectMonsters(ServerPlayer, ServerLevel)` con `Monster.class` y AABB ±25 X/Z, ±5 Y
- [x] 1.4 `WorldContextCollector.java` — ajustar `collectAnimals` al nuevo AABB ±25 X/Z, ±5 Y
- [x] 1.5 `WorldContextCollector.java` — actualizar `collect()` para pasar `collectMonsters` como cuarto campo de `NearbyContext`

## 2. Main sourceset: http

- [x] 2.1 `ButlerHttpClient.java` — añadir serialización de `ctx.nearby().monsters()` en `worldContextToJson` como array `"monsters"` dentro del objeto `"nearby"`

## 3. Compilación (OBLIGATORIO)

- [x] 3.1 `.\gradlew.bat compileJava` → BUILD SUCCESSFUL; guardar informe en `openspec/changes/world-context-mobs/reports/`

## 4. Prueba en el juego (OBLIGATORIO - EL AGENTE LO EJECUTA)

- [x] 4.1 `.\gradlew.bat runClient` → verificar logs de inicio sin errores
- [x] 4.2 Con un cofre registrado con items: hacer `/butler ask "qué hay en mis cofres"` y verificar que `chests` llega al backend con contenido
- [x] 4.3 Con un zombie/araña cerca: hacer `/butler ask "hay algún peligro cerca"` y verificar que `nearby.monsters` llega al backend
- [x] 4.4 Verificar que `nearby.animals` sigue funcionando (ovejas, vacas, etc.)
- [x] 4.5 Documentar resultados en `openspec/changes/world-context-mobs/reports/`

## 5. Cierre (OBLIGATORIO)

- [x] 5.1 Actualizar `CLAUDE.md` — sección world context collector: reflejar campos `animals`/`monsters` separados, nuevo radio ±25/±5, y fix `isLoaded`
- [x] 5.2 PR con `gh` (skill `write-pr-report`)
