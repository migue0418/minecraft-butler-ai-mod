# Phase 4 — Alfred NPC Entity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Register Alfred as a custom `PathfinderMob` entity, give it a Villager placeholder appearance, and add `/butler spawn`, `/butler follow`, `/butler stop` commands plus live pathfinding for `move_to_position`.

**Architecture:** `AlfredEntity extends PathfinderMob` is registered as entity type `ai-butler:alfred`. A custom `AlfredFollowGoal` activates when an `isFollowing` flag is `true`. The client renderer extends `MobRenderer` with the Villager model layer and texture. `ButlerState` gains a `findAlfred(ServerLevel)` helper used by the new commands and executor.

**Tech Stack:** Java 25 / Minecraft 26.1.2 / Fabric API 0.149.1 / Fabric Loom (split source sets) / Mojang mappings

---

## Working directory

`C:\Users\migue\Documents\Proyectos\MinecraftButlerAI`

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `src/main/java/com/miguealguacil/butler/entity/AlfredEntities.java` | EntityType registration |
| Create | `src/main/java/com/miguealguacil/butler/entity/AlfredEntity.java` | PathfinderMob subclass with `isFollowing` flag |
| Create | `src/main/java/com/miguealguacil/butler/entity/AlfredFollowGoal.java` | AI goal: follow nearest player when `isFollowing = true` |
| Create | `src/client/java/com/miguealguacil/butler/client/entity/AlfredEntityRenderer.java` | MobRenderer using Villager model layer and texture |
| Modify | `src/main/java/com/miguealguacil/butler/AIButler.java` | Call `AlfredEntities.register()` in `onInitialize` |
| Modify | `src/client/java/com/miguealguacil/butler/client/AIButlerClient.java` | Register entity renderer |
| Modify | `src/main/java/com/miguealguacil/butler/state/ButlerState.java` | Add `findAlfred(ServerLevel)` |
| Modify | `src/main/java/com/miguealguacil/butler/command/ButlerCommand.java` | Add `spawn`, `follow`, `stop` subcommands |
| Modify | `src/main/java/com/miguealguacil/butler/action/ButlerActionExecutor.java` | `move_to_position` navigates Alfred via pathfinder |

---

## Mojang mappings quick reference (this project only)

| Concept | Mojang class / method |
|---|---|
| Custom mob base | `net.minecraft.world.entity.PathfinderMob` |
| Entity type builder | `net.minecraft.world.entity.EntityType.Builder` |
| Registry | `net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE` |
| Resource location | `ResourceLocation.fromNamespaceAndPath(namespace, path)` |
| AI goal base | `net.minecraft.world.entity.ai.goal.Goal` |
| Float in water | `net.minecraft.world.entity.ai.goal.FloatGoal` |
| Player type | `net.minecraft.world.entity.player.Player` |
| Nearest player | `level.getNearestPlayer(entity, maxDistance)` |
| Navigation | `mob.getNavigation()` → `PathNavigation` |
| Move to entity | `pathNavigation.moveTo(Entity, speed)` |
| Move to coords | `pathNavigation.moveTo(double x, double y, double z, double speed)` |
| Look control | `mob.getLookControl().setLookAt(entity, yRot, xRot)` |
| Server world | `net.minecraft.server.level.ServerLevel` |
| Entity scan | `serverLevel.getEntities(EntityType<T>, Predicate<T>)` → `List<T>` |
| Client renderer base | `net.minecraft.client.renderer.entity.MobRenderer` |
| Villager model | `net.minecraft.client.model.VillagerModel` |
| Model layer key | `net.minecraft.client.model.geom.ModelLayers.VILLAGER` |
| Renderer registry | `net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry` |

---

## Task 1 — Entity registration + AlfredEntity skeleton

**Files:**
- Create: `src/main/java/com/miguealguacil/butler/entity/AlfredEntities.java`
- Create: `src/main/java/com/miguealguacil/butler/entity/AlfredEntity.java`
- Modify: `src/main/java/com/miguealguacil/butler/AIButler.java`

- [ ] **Step 1: Create `AlfredEntities.java`**

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

- [ ] **Step 2: Create `AlfredEntity.java`**

```java
package com.miguealguacil.butler.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.Level;

public class AlfredEntity extends PathfinderMob {

    private boolean isFollowing = false;

    public AlfredEntity(EntityType<? extends AlfredEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    public boolean isFollowing() {
        return isFollowing;
    }

    public void setFollowing(boolean following) {
        this.isFollowing = following;
    }
}
```

Note: `isFollowing` is intentionally NOT persisted to NBT — it resets to `false` on world reload.

- [ ] **Step 3: Replace `AIButler.java`**

```java
package com.miguealguacil.butler;

import com.miguealguacil.butler.command.ButlerCommand;
import com.miguealguacil.butler.entity.AlfredEntities;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AIButler implements ModInitializer {
    public static final String MOD_ID = "ai-butler";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        AlfredEntities.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            ButlerCommand.register(dispatcher)
        );
        LOGGER.info("AI Butler initialized.");
    }
}
```

- [ ] **Step 4: Compile**

```powershell
.\gradlew.bat compileJava
```

Expected: `BUILD SUCCESSFUL`

Common failures:
- `cannot find symbol` on `Registry.register` → import must be `net.minecraft.core.Registry`, not a Fabric registry class.
- `AlfredEntity::new` constructor mismatch → verify `AlfredEntity` has exactly `(EntityType<? extends AlfredEntity>, Level)` as its constructor signature.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/miguealguacil/butler/entity/AlfredEntities.java `
       src/main/java/com/miguealguacil/butler/entity/AlfredEntity.java `
       src/main/java/com/miguealguacil/butler/AIButler.java
git commit -m "feat: register AlfredEntity type and skeleton"
```

---

## Task 2 — AlfredFollowGoal + wire into AlfredEntity

**Files:**
- Create: `src/main/java/com/miguealguacil/butler/entity/AlfredFollowGoal.java`
- Modify: `src/main/java/com/miguealguacil/butler/entity/AlfredEntity.java`

- [ ] **Step 1: Create `AlfredFollowGoal.java`**

```java
package com.miguealguacil.butler.entity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class AlfredFollowGoal extends Goal {

    private final AlfredEntity alfred;
    private final double speed;
    private Player target;

    public AlfredFollowGoal(AlfredEntity alfred, double speed) {
        this.alfred = alfred;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!alfred.isFollowing()) return false;
        Player nearest = alfred.level().getNearestPlayer(alfred, 64.0);
        if (nearest == null) return false;
        target = nearest;
        return alfred.distanceToSqr(target) > 4.0;
    }

    @Override
    public boolean canContinueToUse() {
        return alfred.isFollowing() && target != null;
    }

    @Override
    public void start() {
        alfred.getNavigation().moveTo(target, speed);
    }

    @Override
    public void tick() {
        alfred.getLookControl().setLookAt(target, 10.0f, alfred.getMaxHeadXRot());
        if (alfred.distanceToSqr(target) > 4.0) {
            alfred.getNavigation().moveTo(target, speed);
        } else {
            alfred.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        target = null;
        alfred.getNavigation().stop();
    }
}
```

`distanceToSqr > 4.0` = distance > 2 blocks. The goal activates only when Alfred is farther than 2 blocks, and stops pathing when he closes within 2 blocks.

- [ ] **Step 2: Add the follow goal to `AlfredEntity.registerGoals()`**

Find the `registerGoals()` method in `AlfredEntity.java` and replace it:

```java
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new AlfredFollowGoal(this, 0.6));
    }
```

- [ ] **Step 3: Compile**

```powershell
.\gradlew.bat compileJava
```

Expected: `BUILD SUCCESSFUL`

Common failures:
- `cannot find symbol: getNearestPlayer` → the method may have a different signature in this MC version. Alternative for `canUse()`:
  ```java
  List<Player> players = alfred.level().getEntitiesOfClass(
      Player.class,
      alfred.getBoundingBox().inflate(64.0),
      p -> !p.isSpectator()
  );
  if (players.isEmpty()) return false;
  players.sort(java.util.Comparator.comparingDouble(alfred::distanceToSqr));
  target = players.get(0);
  return alfred.distanceToSqr(target) > 4.0;
  ```
  Add `import java.util.List;` if using this alternative.
- `Flag.MOVE` not resolved → use fully qualified `Goal.Flag.MOVE`.

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/miguealguacil/butler/entity/AlfredFollowGoal.java `
       src/main/java/com/miguealguacil/butler/entity/AlfredEntity.java
git commit -m "feat: add AlfredFollowGoal and wire into AlfredEntity"
```

---

## Task 3 — Client renderer

**Files:**
- Create: `src/client/java/com/miguealguacil/butler/client/entity/AlfredEntityRenderer.java`
- Modify: `src/client/java/com/miguealguacil/butler/client/AIButlerClient.java`

- [ ] **Step 1: Create `AlfredEntityRenderer.java`**

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

- [ ] **Step 2: Replace `AIButlerClient.java`**

```java
package com.miguealguacil.butler.client;

import com.miguealguacil.butler.client.entity.AlfredEntityRenderer;
import com.miguealguacil.butler.entity.AlfredEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class AIButlerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(AlfredEntities.ALFRED, AlfredEntityRenderer::new);
    }
}
```

- [ ] **Step 3: Full build (compiles both main and client source sets)**

```powershell
.\gradlew.bat build
```

Expected: `BUILD SUCCESSFUL`

Common failures:
- `VillagerModel` constructor arity → if `new VillagerModel<>(layerDefinition)` does not compile, check the constructor; in some MC versions it is `new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER), context.bakeLayer(ModelLayers.VILLAGER_BABY))`.
- `ModelLayers.VILLAGER` not found → the field may be named differently; search the class for `villager`.
- `EntityRendererRegistry` not found → ensure the dependency `fabric-api` is on the compile classpath; the class is in `net.fabricmc.fabric.api.client.rendering.v1`.

- [ ] **Step 4: Commit**

```powershell
git add src/client/java/com/miguealguacil/butler/client/entity/AlfredEntityRenderer.java `
       src/client/java/com/miguealguacil/butler/client/AIButlerClient.java
git commit -m "feat: add AlfredEntityRenderer with Villager placeholder appearance"
```

---

## Task 4 — ButlerState.findAlfred + spawn/follow/stop commands

**Files:**
- Modify: `src/main/java/com/miguealguacil/butler/state/ButlerState.java`
- Modify: `src/main/java/com/miguealguacil/butler/command/ButlerCommand.java`

- [ ] **Step 1: Replace `ButlerState.java`**

```java
package com.miguealguacil.butler.state;

import com.miguealguacil.butler.entity.AlfredEntity;
import com.miguealguacil.butler.entity.AlfredEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;

public final class ButlerState {
    private static BlockPos savedPosition = null;

    private ButlerState() {}

    public static void setSavedPosition(BlockPos pos) {
        savedPosition = pos;
    }

    public static BlockPos getSavedPosition() {
        return savedPosition;
    }

    public static boolean hasSavedPosition() {
        return savedPosition != null;
    }

    public static Optional<AlfredEntity> findAlfred(ServerLevel level) {
        List<AlfredEntity> found = level.getEntities(AlfredEntities.ALFRED, e -> true);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }
}
```

- [ ] **Step 2: Replace `ButlerCommand.java`**

```java
package com.miguealguacil.butler.command;

import com.miguealguacil.butler.AIButler;
import com.miguealguacil.butler.action.ButlerAction;
import com.miguealguacil.butler.action.ButlerActionExecutor;
import com.miguealguacil.butler.entity.AlfredEntities;
import com.miguealguacil.butler.entity.AlfredEntity;
import com.miguealguacil.butler.http.ButlerHttpClient;
import com.miguealguacil.butler.state.ButlerState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public final class ButlerCommand {
    private ButlerCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("butler")
                .then(Commands.literal("ping")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("Alfred está operativo."), false);
                        return 1;
                    }))
                .then(Commands.literal("pos")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        ServerPlayer player = source.getPlayerOrException();
                        BlockPos pos = player.blockPosition();
                        source.sendSuccess(
                            () -> Component.literal("Tu posición es: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()),
                            false);
                        return 1;
                    }))
                .then(Commands.literal("here")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        ServerPlayer player = source.getPlayerOrException();
                        BlockPos pos = player.blockPosition();
                        ButlerState.setSavedPosition(pos);
                        source.sendSuccess(
                            () -> Component.literal("Posición guardada para Alfred: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()),
                            false);
                        return 1;
                    }))
                .then(Commands.literal("target")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        BlockPos pos = ButlerState.getSavedPosition();
                        if (pos != null) {
                            source.sendSuccess(
                                () -> Component.literal("Objetivo actual de Alfred: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()),
                                false);
                        } else {
                            source.sendSuccess(
                                () -> Component.literal("No hay posición guardada. Usa /butler here primero."),
                                false);
                        }
                        return 1;
                    }))
                .then(Commands.literal("mock")
                    .executes(ctx -> {
                        ButlerActionExecutor.execute(
                            new ButlerAction("speak", "He recibido una acción mock correctamente.", null, null, null),
                            ctx.getSource());
                        return 1;
                    }))
                .then(Commands.literal("spawn")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        ServerPlayer player = source.getPlayerOrException();
                        ServerLevel level = player.serverLevel();
                        Optional<AlfredEntity> existing = ButlerState.findAlfred(level);
                        if (existing.isPresent()) {
                            existing.get().teleportTo(player.getX(), player.getY(), player.getZ());
                            source.sendSuccess(
                                () -> Component.literal("Alfred ha sido teleportado a tu posición."), false);
                        } else {
                            AlfredEntity alfred = new AlfredEntity(AlfredEntities.ALFRED, level);
                            alfred.moveTo(player.getX(), player.getY(), player.getZ(), 0f, 0f);
                            level.addFreshEntity(alfred);
                            source.sendSuccess(
                                () -> Component.literal("Alfred ha aparecido."), false);
                        }
                        return 1;
                    }))
                .then(Commands.literal("follow")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        ServerLevel level = source.getLevel();
                        Optional<AlfredEntity> alfred = ButlerState.findAlfred(level);
                        if (alfred.isPresent()) {
                            alfred.get().setFollowing(true);
                            source.sendSuccess(
                                () -> Component.literal("[Alfred] Siguiéndote."), false);
                        } else {
                            source.sendSuccess(
                                () -> Component.literal("[Alfred] Alfred no está en el mundo. Usa /butler spawn primero."),
                                false);
                        }
                        return 1;
                    }))
                .then(Commands.literal("stop")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        ServerLevel level = source.getLevel();
                        Optional<AlfredEntity> alfred = ButlerState.findAlfred(level);
                        if (alfred.isPresent()) {
                            alfred.get().setFollowing(false);
                            alfred.get().getNavigation().stop();
                            source.sendSuccess(
                                () -> Component.literal("[Alfred] Me detengo."), false);
                        } else {
                            source.sendSuccess(
                                () -> Component.literal("[Alfred] Alfred no está en el mundo."),
                                false);
                        }
                        return 1;
                    }))
                .then(Commands.literal("ask")
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            MinecraftServer server = source.getServer();
                            String message = StringArgumentType.getString(ctx, "message");

                            source.sendSuccess(
                                () -> Component.literal("[Alfred] Procesando..."), false);

                            ButlerHttpClient.sendAsync(message)
                                .thenAccept(actions ->
                                    server.execute(() ->
                                        actions.forEach(action ->
                                            ButlerActionExecutor.execute(action, source))
                                    )
                                )
                                .exceptionally(ex -> {
                                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                                    String msg;
                                    if (cause instanceof ButlerHttpClient.AuthException) {
                                        msg = "[Alfred] No pude autenticarme con el servidor.";
                                    } else if (cause instanceof ButlerHttpClient.ServerException) {
                                        msg = "[Alfred] Error del servidor.";
                                    } else {
                                        msg = "[Alfred] No pude contactar con el servidor.";
                                    }
                                    AIButler.LOGGER.error("Butler ask error", cause);
                                    server.execute(() ->
                                        source.sendSuccess(() -> Component.literal(msg), false));
                                    return null;
                                });

                            return 1;
                        })))
        );
    }
}
```

- [ ] **Step 3: Compile**

```powershell
.\gradlew.bat compileJava
```

Expected: `BUILD SUCCESSFUL`

Common failures:
- `getEntities(EntityType, Predicate)` not found → the method may be named differently. Alternative using `getEntitiesOfClass`:
  ```java
  public static Optional<AlfredEntity> findAlfred(ServerLevel level) {
      List<AlfredEntity> found = level.getEntitiesOfClass(
          AlfredEntity.class,
          new net.minecraft.world.phys.AABB(
              Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE,
              Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE),
          e -> true);
      return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
  }
  ```
  Or simply: `level.getEntitiesOfClass(AlfredEntity.class, level.getWorldBorder().createBoundingBox(), e -> true)`.
- `source.getLevel()` reports return type `Level` instead of `ServerLevel` → add cast: `(ServerLevel) source.getLevel()`.

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/miguealguacil/butler/state/ButlerState.java `
       src/main/java/com/miguealguacil/butler/command/ButlerCommand.java
git commit -m "feat: add spawn/follow/stop commands and findAlfred helper"
```

---

## Task 5 — ButlerActionExecutor: live move_to_position

**Files:**
- Modify: `src/main/java/com/miguealguacil/butler/action/ButlerActionExecutor.java`

- [ ] **Step 1: Replace `ButlerActionExecutor.java`**

```java
package com.miguealguacil.butler.action;

import com.miguealguacil.butler.entity.AlfredEntity;
import com.miguealguacil.butler.state.ButlerState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

public final class ButlerActionExecutor {
    private ButlerActionExecutor() {}

    public static void execute(ButlerAction action, CommandSourceStack source) {
        switch (action.type()) {
            case "speak" ->
                source.sendSuccess(() -> Component.literal("[Alfred] " + action.message()), false);
            case "move_to_position" -> {
                if (action.x() != null && action.y() != null && action.z() != null) {
                    int x = action.x(), y = action.y(), z = action.z();
                    ServerLevel level = source.getLevel();
                    Optional<AlfredEntity> alfred = ButlerState.findAlfred(level);
                    alfred.ifPresent(a -> a.getNavigation().moveTo(x, y, z, 0.6));
                    source.sendSuccess(() -> Component.literal(
                        "[Alfred] Me dirijo a " + x + " " + y + " " + z + "."), false);
                } else {
                    source.sendSuccess(() -> Component.literal("[Alfred] Destino no especificado."), false);
                }
            }
            default ->
                source.sendSuccess(() -> Component.literal("[Alfred] Acción desconocida: " + action.type()), false);
        }
    }
}
```

- [ ] **Step 2: Compile**

```powershell
.\gradlew.bat compileJava
```

Expected: `BUILD SUCCESSFUL`

Common failures:
- `source.getLevel()` returns `Level` → add cast: `(ServerLevel) source.getLevel()`.

- [ ] **Step 3: Full build**

```powershell
.\gradlew.bat build
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/miguealguacil/butler/action/ButlerActionExecutor.java
git commit -m "feat: move_to_position navigates Alfred via pathfinder"
```

---

## Task 6 — In-game verification

No automated tests exist for Minecraft entity behavior — verification is in-game.

**Prerequisites:** Backend running in a separate terminal:

```powershell
cd "C:\Users\migue\Documents\Proyectos\MinecraftButlerAI Backend"
.venv\Scripts\Activate.ps1
uvicorn app.main:app --reload --port 8000
```

- [ ] **Step 1: Launch Minecraft**

```powershell
.\gradlew.bat runClient
```

- [ ] **Step 2: Open a singleplayer world with cheats enabled**

- [ ] **Step 3: Test spawn**

```
/butler spawn
```

Expected: Alfred (Aldeano) aparece a los pies del jugador. Chat: `Alfred ha aparecido.`

- [ ] **Step 4: Test follow**

```
/butler follow
```

Expected: `[Alfred] Siguiéndote.` Alfred comienza a caminar detrás del jugador al moverse.

- [ ] **Step 5: Test stop**

```
/butler stop
```

Expected: `[Alfred] Me detengo.` Alfred se detiene inmediatamente.

- [ ] **Step 6: Test spawn with Alfred already present**

```
/butler spawn
```

Expected: Alfred se teleporta a la posición del jugador (no aparece un segundo Alfred). Chat: `Alfred ha sido teleportado a tu posición.`

- [ ] **Step 7: Test persistence**

Guardar y salir del mundo (`/save-all` + Esc → Guardar y salir). Volver a entrar. Alfred debe estar en la misma posición. `isFollowing` es `false` — correcto por diseño.

- [ ] **Step 8: Test move_to_position (backend activo)**

```
/butler ask ve a 100 64 -50
```

Expected: `[Alfred] Procesando...` seguido de `[Alfred] Me dirijo a 100 64 -50.` Alfred comienza a navegar hacia esas coordenadas.

- [ ] **Step 9: Regression check — todos los comandos anteriores**

```
/butler ping
/butler pos
/butler here
/butler target
/butler mock
```

Expected: todos funcionan sin regresiones.

---

## Command reference

| Purpose | Command |
|---------|---------|
| Compile main source set | `.\gradlew.bat compileJava` |
| Full build (main + client) | `.\gradlew.bat build` |
| Launch Minecraft | `.\gradlew.bat runClient` |
| Start backend | `uvicorn app.main:app --reload --port 8000` |
