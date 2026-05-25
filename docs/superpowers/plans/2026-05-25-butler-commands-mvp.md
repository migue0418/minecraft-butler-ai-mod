# Butler Commands MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add five `/butler` subcommands (ping, pos, here, target, mock) to the existing Fabric mod so it compiles and works in singleplayer.

**Architecture:** Commands are registered in `AIButler.onInitialize()` via Fabric's `CommandRegistrationCallback`. A static `ButlerState` holds the one saved `BlockPos` in memory. A `ButlerAction` record plus `ButlerActionExecutor` form the mock action pipeline. `ButlerCommand` owns the full Brigadier tree.

**Tech Stack:** Java 25, Minecraft 26.1.2, Fabric Loader 0.19.2, Fabric API 0.149.1+26.1.2, Gradle + Fabric Loom 1.16-SNAPSHOT

---

## Diagnosis

| Item | Value |
|------|-------|
| Root package | `com.miguealguacil.butler` |
| Main initializer | `src/main/java/com/miguealguacil/butler/AIButler.java` |
| Mod ID | `ai-butler` |
| Minecraft version | `26.1.2` |
| Fabric Loader | `0.19.2` |
| Fabric API | `0.149.1+26.1.2` (already declared as `implementation` in `build.gradle`) |
| Loom | `1.16-SNAPSHOT` |
| Java source/target | 25 |
| Current `onInitialize()` | Only logs "Hello AI Butler!" — no commands registered |
| Build state | `.class` files present in `build/` — project has compiled before |

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `src/main/java/com/miguealguacil/butler/state/ButlerState.java` | In-memory saved `BlockPos` |
| Create | `src/main/java/com/miguealguacil/butler/action/ButlerAction.java` | Action data record |
| Create | `src/main/java/com/miguealguacil/butler/action/ButlerActionExecutor.java` | Execute typed actions |
| Create | `src/main/java/com/miguealguacil/butler/command/ButlerCommand.java` | Full Brigadier command tree |
| Modify | `src/main/java/com/miguealguacil/butler/AIButler.java` | Register commands in `onInitialize()` |

---

## Risks and Version-Specific Notes

### Risk 1 — `getPlayer()` vs `getPlayerOrThrow()`
`ServerCommandSource.getPlayer()` throws `CommandSyntaxException` (handled by Brigadier automatically). In some MC versions the method is named `getPlayerOrThrow()`. If `getPlayer()` causes a compile error, rename to `getPlayerOrThrow()` everywhere in `ButlerCommand.java`.

### Risk 2 — `sendFeedback` signature
Since MC 1.19.4, `sendFeedback` takes a `Supplier<Text>`, not a `Text` directly:
```java
source.sendFeedback(() -> Text.literal("..."), false);  // correct
source.sendFeedback(Text.literal("..."), false);         // old, may not compile
```
If the supplier form fails, check IntelliJ's generated sources (Gradle task `genSources`) for the actual signature.

### Risk 3 — `CommandManager.literal()` location
The plan uses `net.minecraft.server.command.CommandManager.literal(String)`. If that import is missing, the Brigadier equivalent works:
```java
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
```

### Risk 4 — MC 26.1.2 package stability
`26.1.2` uses the new Mojang year-based versioning (2026). Core API packages (`net.minecraft.server.command`, `net.minecraft.util.math`, `net.minecraft.text`) have been stable across Fabric ecosystem releases. If any import fails, use IntelliJ's "Navigate → Class" (Ctrl+N) on the deobfuscated sources after running `.\gradlew.bat genSources`.

### Risk 5 — `CommandRegistrationCallback` package
Package is `net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback`. Fabric API has used `v2` since 0.56+; version 0.149 should be fine.

---

## Task 1 — Create `ButlerState`

**Files:**
- Create: `src/main/java/com/miguealguacil/butler/state/ButlerState.java`

- [ ] **Step 1: Create the file**

```java
package com.miguealguacil.butler.state;

import net.minecraft.util.math.BlockPos;

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
}
```

- [ ] **Step 2: Compile to verify no import errors**

```powershell
.\gradlew.bat compileJava
```

Expected: `BUILD SUCCESSFUL`. If `BlockPos` import fails, check the correct path via IntelliJ (`Ctrl+N` → type `BlockPos`).

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/miguealguacil/butler/state/ButlerState.java
git commit -m "feat: add ButlerState in-memory position holder"
```

---

## Task 2 — Create `ButlerAction` and `ButlerActionExecutor`

**Files:**
- Create: `src/main/java/com/miguealguacil/butler/action/ButlerAction.java`
- Create: `src/main/java/com/miguealguacil/butler/action/ButlerActionExecutor.java`

- [ ] **Step 1: Create `ButlerAction.java`**

```java
package com.miguealguacil.butler.action;

public record ButlerAction(String type, String message) {}
```

- [ ] **Step 2: Create `ButlerActionExecutor.java`**

```java
package com.miguealguacil.butler.action;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class ButlerActionExecutor {
    private ButlerActionExecutor() {}

    public static void execute(ButlerAction action, ServerCommandSource source) {
        switch (action.type()) {
            case "speak" ->
                source.sendFeedback(() -> Text.literal("[Alfred] " + action.message()), false);
            default ->
                source.sendFeedback(() -> Text.literal("[Alfred] Acción desconocida: " + action.type()), false);
        }
    }
}
```

- [ ] **Step 3: Compile**

```powershell
.\gradlew.bat compileJava
```

Expected: `BUILD SUCCESSFUL`. If `sendFeedback` fails to compile, check whether it takes `Supplier<Text>` or bare `Text` in the generated sources.

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/miguealguacil/butler/action/
git commit -m "feat: add ButlerAction record and ButlerActionExecutor"
```

---

## Task 3 — Create `ButlerCommand`

**Files:**
- Create: `src/main/java/com/miguealguacil/butler/command/ButlerCommand.java`

- [ ] **Step 1: Create `ButlerCommand.java`**

```java
package com.miguealguacil.butler.command;

import com.miguealguacil.butler.action.ButlerAction;
import com.miguealguacil.butler.action.ButlerActionExecutor;
import com.miguealguacil.butler.state.ButlerState;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class ButlerCommand {
    private ButlerCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("butler")
                .then(CommandManager.literal("ping")
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(
                            () -> Text.literal("Alfred está operativo."), false);
                        return 1;
                    }))
                .then(CommandManager.literal("pos")
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        ServerPlayerEntity player = source.getPlayer();
                        BlockPos pos = player.getBlockPos();
                        source.sendFeedback(
                            () -> Text.literal("Tu posición es: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()),
                            false);
                        return 1;
                    }))
                .then(CommandManager.literal("here")
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        ServerPlayerEntity player = source.getPlayer();
                        BlockPos pos = player.getBlockPos();
                        ButlerState.setSavedPosition(pos);
                        source.sendFeedback(
                            () -> Text.literal("Posición guardada para Alfred: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()),
                            false);
                        return 1;
                    }))
                .then(CommandManager.literal("target")
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        BlockPos pos = ButlerState.getSavedPosition();
                        if (pos != null) {
                            source.sendFeedback(
                                () -> Text.literal("Objetivo actual de Alfred: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()),
                                false);
                        } else {
                            source.sendFeedback(
                                () -> Text.literal("No hay posición guardada. Usa /butler here primero."),
                                false);
                        }
                        return 1;
                    }))
                .then(CommandManager.literal("mock")
                    .executes(ctx -> {
                        ButlerActionExecutor.execute(
                            new ButlerAction("speak", "He recibido una acción mock correctamente."),
                            ctx.getSource());
                        return 1;
                    }))
        );
    }
}
```

- [ ] **Step 2: Compile**

```powershell
.\gradlew.bat compileJava
```

Expected: `BUILD SUCCESSFUL`. Common failures and fixes:
- `getPlayer()` not found → rename to `getPlayerOrThrow()`
- `CommandManager` not found → add import `import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;` and use `literal("butler")` directly
- `ServerPlayerEntity` not found → check correct path with IntelliJ `Ctrl+N`

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/miguealguacil/butler/command/ButlerCommand.java
git commit -m "feat: add ButlerCommand Brigadier tree with ping/pos/here/target/mock"
```

---

## Task 4 — Register commands in `AIButler.java`

**Files:**
- Modify: `src/main/java/com/miguealguacil/butler/AIButler.java`

- [ ] **Step 1: Replace the contents of `AIButler.java`**

```java
package com.miguealguacil.butler;

import com.miguealguacil.butler.command.ButlerCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AIButler implements ModInitializer {
    public static final String MOD_ID = "ai-butler";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            ButlerCommand.register(dispatcher)
        );
        LOGGER.info("AI Butler commands registered.");
    }
}
```

- [ ] **Step 2: Compile the full project**

```powershell
.\gradlew.bat compileJava
```

Expected: `BUILD SUCCESSFUL`. If `CommandRegistrationCallback` is not found, verify that `fabric-api` is declared in `build.gradle` dependencies (it is — see `implementation "net.fabricmc.fabric-api:fabric-api:..."`).

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/miguealguacil/butler/AIButler.java
git commit -m "feat: register butler commands from ModInitializer"
```

---

## Task 5 — Integration test in-game

- [ ] **Step 1: Launch Minecraft client**

```powershell
.\gradlew.bat runClient
```

Wait for the game to fully load (first run may download assets).

- [ ] **Step 2: Create a singleplayer world with cheats enabled**

New World → More Options → Allow Cheats: ON → Create World.

- [ ] **Step 3: Test all five commands**

Open chat (`T`) and run each command. Expected results:

| Command | Expected output |
|---------|----------------|
| `/butler ping` | `Alfred está operativo.` |
| `/butler pos` | `Tu posición es: X Y Z` (your current coordinates) |
| `/butler here` | `Posición guardada para Alfred: X Y Z` |
| `/butler target` | `Objetivo actual de Alfred: X Y Z` (same as above) |
| `/butler mock` | `[Alfred] He recibido una acción mock correctamente.` |

Also verify `/butler target` before running `/butler here` shows:
`No hay posición guardada. Usa /butler here primero.`

- [ ] **Step 4: Commit if all commands work**

```powershell
git add -A
git commit -m "chore: verify butler MVP commands work in-game"
```

---

## Gradle command reference

| Purpose | Command |
|---------|---------|
| Compile only (fast check) | `.\gradlew.bat compileJava` |
| Full build | `.\gradlew.bat build` |
| Launch game | `.\gradlew.bat runClient` |
| Generate deobfuscated sources for IntelliJ inspection | `.\gradlew.bat genSources` |