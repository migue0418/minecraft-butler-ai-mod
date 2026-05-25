# Phase 4 — Alfred NPC Entity Design

**Date:** 2026-05-25
**Status:** Approved

---

## Goal

Introduce Alfred as a real entity in the Minecraft world. Phase 4 proves that a custom `PathfinderMob` can be registered, spawned, rendered with a Villager placeholder appearance, and controlled via commands. Alfred can follow the player on command and navigate to coordinates received from the backend.

---

## Scope Boundaries

In Phase 4:
- Custom entity class (`AlfredEntity extends PathfinderMob`)
- Entity type registration
- Villager model/texture as placeholder renderer (no custom model, no GeckoLib, no Blockbench)
- `/butler spawn` — create or teleport Alfred
- `/butler follow` / `/butler stop` — toggle follow AI
- `move_to_position` action navigates Alfred via pathfinder (replaces Phase 3 text placeholder)
- Alfred persists in the world across sessions (standard Minecraft entity save/load)
- `isFollowing` flag resets to `false` on world reload (not persisted in NBT)

Not in Phase 4:
- Custom model or texture for Alfred
- GeckoLib / Blockbench assets
- Taming or ownership system
- Multiple Alfred instances
- Persistent follow state across sessions
- Voice or LLM integration

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `src/main/java/com/miguealguacil/butler/entity/AlfredEntity.java` | PathfinderMob subclass with `isFollowing` flag and goal registration |
| Create | `src/main/java/com/miguealguacil/butler/entity/AlfredEntities.java` | EntityType registration |
| Create | `src/main/java/com/miguealguacil/butler/entity/AlfredFollowGoal.java` | AI goal: follows nearest player when `isFollowing = true` |
| Create | `src/client/java/com/miguealguacil/butler/client/entity/AlfredEntityRenderer.java` | MobRenderer using Villager model layer and texture |
| Modify | `src/main/java/com/miguealguacil/butler/AIButler.java` | Call `AlfredEntities.register()` in `onInitialize` |
| Modify | `src/client/java/com/miguealguacil/butler/client/AIButlerClient.java` | Register `AlfredEntityRenderer` for Alfred's entity type |
| Modify | `src/main/java/com/miguealguacil/butler/state/ButlerState.java` | Add `findAlfred(ServerLevel)` helper |
| Modify | `src/main/java/com/miguealguacil/butler/command/ButlerCommand.java` | Add `spawn`, `follow`, `stop` subcommands |
| Modify | `src/main/java/com/miguealguacil/butler/action/ButlerActionExecutor.java` | `move_to_position` navigates Alfred via pathfinder |

---

## Entity Registration

### `AlfredEntities.java`

```java
package com.miguealguacil.butler.entity;

import com.miguealguacil.butler.AIButler;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class AlfredEntities {
    public static final EntityType<AlfredEntity> ALFRED = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath(AIButler.MOD_ID, "alfred"),
        EntityType.Builder.<AlfredEntity>of(AlfredEntity::new, MobCategory.MISC)
            .sized(0.6f, 1.95f)
            .build()
    );

    private AlfredEntities() {}

    public static void register() {}
}
```

Called from `AIButler.onInitialize()`:
```java
AlfredEntities.register();
```

---

## AlfredEntity

### `AlfredEntity.java`

- Extends `PathfinderMob`
- Field: `private boolean isFollowing = false`
- `registerGoals()`:
  - Priority 0: `FloatGoal(this)` — prevents sinking in water
  - Priority 1: `new AlfredFollowGoal(this, 0.6)` — follow player when active
- Public `isFollowing()` / `setFollowing(boolean)` accessors
- `isFollowing` is not persisted in NBT — resets to `false` on world reload

---

## AlfredFollowGoal

### `AlfredFollowGoal.java`

- Extends `Goal`
- Flags: `Goal.Flag.MOVE`, `Goal.Flag.LOOK`
- `canUse()`: returns `false` if `!alfred.isFollowing()`; finds nearest player within 64 blocks; returns `true` if player exists and distance > 3 blocks
- `start()`: calls `alfred.getNavigation().moveTo(player, speed)`
- `tick()`: updates look at player; re-issues `moveTo` if distance > 2 blocks
- `canContinueToUse()`: `alfred.isFollowing() && target != null`
- `stop()`: nulls target, calls `alfred.getNavigation().stop()`

Speed: `0.6` (matching standard Villager walk speed).
Stop distance: 2 blocks (Alfred stops when within 2 blocks of player).

---

## Client Renderer

### `AlfredEntityRenderer.java`

```java
package com.miguealguacil.butler.client.entity;

import com.miguealguacil.butler.entity.AlfredEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class AlfredEntityRenderer extends MobRenderer<AlfredEntity, VillagerModel<AlfredEntity>> {
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/villager/villager.png");

    public AlfredEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(AlfredEntity entity) {
        return TEXTURE;
    }
}
```

Registered in `AIButlerClient.onInitializeClient()`:
```java
EntityRendererRegistry.register(AlfredEntities.ALFRED, AlfredEntityRenderer::new);
```

---

## ButlerState

`findAlfred(ServerLevel level)` scans all entities of type `AlfredEntities.ALFRED` and returns the first match:

```java
public static Optional<AlfredEntity> findAlfred(ServerLevel level) {
    List<AlfredEntity> found = level.getEntities(AlfredEntities.ALFRED, e -> true);
    return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
}
```

---

## Commands

### `/butler spawn`

1. Requires player source (`getPlayerOrException()`).
2. Calls `ButlerState.findAlfred(player.serverLevel())`.
3. If Alfred exists: `alfred.teleportTo(player.getX(), player.getY(), player.getZ())` → message "Alfred ha sido teleportado a tu posición."
4. If Alfred does not exist: create `new AlfredEntity(AlfredEntities.ALFRED, level)`, call `moveTo(x, y, z, 0f, 0f)`, `level.addFreshEntity(alfred)` → message "Alfred ha aparecido."

### `/butler follow`

1. Requires player source.
2. Calls `findAlfred`. If not found → "Alfred no está en el mundo. Usa /butler spawn primero."
3. If found: `alfred.setFollowing(true)` → "[Alfred] Siguiéndote."

### `/butler stop`

1. Requires player source.
2. Calls `findAlfred`. If not found → "Alfred no está en el mundo."
3. If found: `alfred.setFollowing(false)` → "[Alfred] Me detengo."

---

## ButlerActionExecutor — move_to_position

Updated behavior:
1. Search for Alfred in `source.getLevel()` cast to `ServerLevel`.
2. If Alfred exists and coordinates are non-null: call `alfred.getNavigation().moveTo(x, y, z, 0.6)` → message "[Alfred] Me dirijo a X Y Z."
3. If Alfred does not exist: fall back to text message "[Alfred] Me dirijo a X Y Z." (same as Phase 3 placeholder).
4. If coordinates are null: "[Alfred] Destino no especificado." (unchanged).

---

## Verification (in-game)

| Command | Expected result |
|---------|-----------------|
| `/butler spawn` | Alfred (Aldeano) aparece a los pies del jugador |
| `/butler follow` | Alfred sigue al jugador al caminar |
| `/butler stop` | Alfred se detiene |
| `/butler spawn` (Alfred ya existe) | Alfred se teleporta, no aparece uno nuevo |
| Salir y reentrar al mundo | Alfred persiste en la misma posición, `isFollowing` = false |
| `/butler ask ve a 100 64 -50` (backend activo) | Alfred navega hacia 100 64 -50 |
| `/butler mock` | "[Alfred] He recibido una acción mock correctamente." — sin errores |
| `/butler ping`, `/butler pos`, `/butler here`, `/butler target` | Sin regresiones |
