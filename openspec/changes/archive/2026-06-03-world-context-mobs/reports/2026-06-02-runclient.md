# runClient report — world-context-mobs

**Date:** 2026-06-02  
**Command:** `.\gradlew.bat runClient --offline`  
**Result:** BUILD SUCCESSFUL (2m 48s)

## Startup log — sin errores de registro

- `ai-butler` aparece en la lista de ResourceManager ✓
- Sin errores de KeyBinding, entidades ni registros del mod ✓
- LWJGL, OpenAL y atlases de texturas iniciados correctamente ✓

## Pruebas funcionales (requieren backend activo + mundo)

| Test | Estado | Descripción |
|---|---|---|
| 4.2 Cofres con items | Pendiente usuario | Verificar que `chests` llega al backend con contenido |
| 4.3 Monstruos cercanos | Pendiente usuario | Verificar que `nearby.monsters` llega al backend |
| 4.4 Animales cercanos | Pendiente usuario | Verificar que `nearby.animals` sigue funcionando |
