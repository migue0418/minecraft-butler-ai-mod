# Phase 5 — Chest Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add named chest registry with JSON persistence, inspect/move/distribute commands, and stubbed backend endpoints documented for Phase 6 LLM integration.

**Architecture:** Java handles all chest manipulation locally. `ChestRegistry` persists named chest aliases to `run/butler_chests.json` via Gson (already in project). `ChestItemMover` provides reusable `tryInsert`/`tryMoveAll` helpers called by the move and distribute commands. Seven new `/butler chest` subcommands are added as private static methods in `ButlerCommand`. The backend (`chest_router.py`) is a stub with inline Phase 6 documentation — Java does NOT call it in Phase 5.

**Tech Stack:** Java 21, Fabric API 0.149.1+26.1.2, Gson (already a Minecraft dependency), FastAPI/Pydantic (backend mockup only)

---

### Task 1: Create `ChestEntry` record

**Files:**
- Create: `src/main/java/com/miguealguacil/butler/chest/ChestEntry.java`

- [ ] **Step 1: Create the file**

```java
package com.miguealguacil.butler.chest;

import net.minecraft.core.BlockPos;
import java.util.List;

public record ChestEntry(
    String name,
    int x, int y, int z,
    String dimension,
    List<String> accepts
) {
    public BlockPos blockPos() {
        return new BlockPos(x, y, z);
    }
}
```

- [ ] **Step 2: Compile**

```
.\gradlew.bat compileJava
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```
git add src/main/java/com/miguealguacil/butler/chest/ChestEntry.java
git commit -m "feat: add ChestEntry record for Phase 5 chest registry"
```

---

### Task 2: Create `ChestRegistry`

**Files:**
- Create: `src/main/java/com/miguealguacil/butler/chest/ChestRegistry.java`

- [ ] **Step 1: Create the file**

```java
package com.miguealguacil.butler.chest;

import com.google.gson.*;
import com.miguealguacil.butler.AIButler;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class ChestRegistry {

    private static final Path FILE = Path.of("butler_chests.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, ChestEntry> registry = new LinkedHashMap<>();

    private ChestRegistry() {}

    public static void load() {
        if (!Files.exists(FILE)) return;
        try (Reader reader = Files.newBufferedReader(FILE)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("chests")) return;
            registry.clear();
            for (JsonElement el : root.getAsJsonArray("chests")) {
                JsonObject obj = el.getAsJsonObject();
                List<String> accepts = new ArrayList<>();
                if (obj.has("accepts"))
                    for (JsonElement a : obj.getAsJsonArray("accepts"))
                        accepts.add(a.getAsString());
                registry.put(obj.get("name").getAsString(), new ChestEntry(
                    obj.get("name").getAsString(),
                    obj.get("x").getAsInt(),
                    obj.get("y").getAsInt(),
                    obj.get("z").getAsInt(),
                    obj.get("dimension").getAsString(),
                    accepts
                ));
            }
            AIButler.LOGGER.info("Butler: loaded {} chest(s)", registry.size());
        } catch (IOException e) {
            AIButler.LOGGER.error("Butler: failed to load {}", FILE, e);
        }
    }

    public static void save() {
        JsonObject root = new JsonObject();
        JsonArray chests = new JsonArray();
        for (ChestEntry e : registry.values()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", e.name());
            obj.addProperty("x", e.x());
            obj.addProperty("y", e.y());
            obj.addProperty("z", e.z());
            obj.addProperty("dimension", e.dimension());
            JsonArray accepts = new JsonArray();
            e.accepts().forEach(accepts::add);
            obj.add("accepts", accepts);
            chests.add(obj);
        }
        root.add("chests", chests);
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            AIButler.LOGGER.error("Butler: failed to save {}", FILE, e);
        }
    }

    public static void register(ChestEntry entry)  { registry.put(entry.name(), entry); save(); }
    public static void unregister(String name)     { registry.remove(name); save(); }
    public static Optional<ChestEntry> get(String name) { return Optional.ofNullable(registry.get(name)); }
    public static Collection<ChestEntry> getAll()  { return registry.values(); }

    public static void setAccepts(String name, List<String> items) {
        get(name).ifPresent(e -> {
            registry.put(name, new ChestEntry(e.name(), e.x(), e.y(), e.z(), e.dimension(), items));
            save();
        });
    }
}
```

- [ ] **Step 2: Compile**

```
.\gradlew.bat compileJava
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```
git add src/main/java/com/miguealguacil/butler/chest/ChestRegistry.java
git commit -m "feat: add ChestRegistry with JSON persistence"
```

---

### Task 3: Wire `ChestRegistry.load()` into `AIButler`

**Files:**
- Modify: `src/main/java/com/miguealguacil/butler/AIButler.java`

- [ ] **Step 1: Add imports and `SERVER_STARTED` listener**

Add these two imports to `AIButler.java`:
```java
import com.miguealguacil.butler.chest.ChestRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
```

Add one line inside `onInitialize()`, after `AlfredEntities.register()`:
```java
ServerLifecycleEvents.SERVER_STARTED.register(server -> ChestRegistry.load());
```

Full `onInitialize()` after the change:
```java
@Override
public void onInitialize() {
    AlfredEntities.register();
    ServerLifecycleEvents.SERVER_STARTED.register(server -> ChestRegistry.load());
    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
        ButlerCommand.register(dispatcher)
    );
    LOGGER.info("AI Butler initialized.");
}
```

- [ ] **Step 2: Compile**

```
.\gradlew.bat compileJava
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```
git add src/main/java/com/miguealguacil/butler/AIButler.java
git commit -m "feat: load ChestRegistry on server start"
```

---

### Task 4: Create `ChestItemMover`

**Files:**
- Create: `src/main/java/com/miguealguacil/butler/chest/ChestItemMover.java`

- [ ] **Step 1: Create the file**

```java
package com.miguealguacil.butler.chest;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class ChestItemMover {

    private ChestItemMover() {}

    /**
     * Inserts as many items from {@code stack} as possible into {@code dest}.
     * Modifies {@code dest} in place. Does NOT modify {@code stack}.
     * Calls {@code dest.setChanged()} if any items were inserted.
     *
     * @return number of items actually inserted
     */
    public static int tryInsert(ItemStack stack, Container dest) {
        int inserted = 0;
        int remaining = stack.getCount();

        // Pass 1: merge with existing matching stacks
        for (int i = 0; i < dest.getContainerSize() && remaining > 0; i++) {
            ItemStack slot = dest.getItem(i);
            if (!slot.isEmpty() && slot.getItem() == stack.getItem()) {
                int canFit = Math.min(remaining, slot.getMaxStackSize() - slot.getCount());
                if (canFit > 0) {
                    slot.grow(canFit);
                    dest.setItem(i, slot);
                    remaining -= canFit;
                    inserted += canFit;
                }
            }
        }

        // Pass 2: fill empty slots
        for (int i = 0; i < dest.getContainerSize() && remaining > 0; i++) {
            if (dest.getItem(i).isEmpty()) {
                int canFit = Math.min(remaining, stack.getMaxStackSize());
                dest.setItem(i, stack.copyWithCount(canFit));
                remaining -= canFit;
                inserted += canFit;
            }
        }

        if (inserted > 0) dest.setChanged();
        return inserted;
    }

    /**
     * Moves as much of each slot in {@code source} as possible into {@code dest}.
     * Modifies both containers in place.
     *
     * @return int[]{totalMoved, totalRemaining}
     */
    public static int[] tryMoveAll(Container source, Container dest) {
        int totalMoved = 0;
        int totalRemaining = 0;

        for (int i = 0; i < source.getContainerSize(); i++) {
            ItemStack slot = source.getItem(i);
            if (slot.isEmpty()) continue;

            int count = slot.getCount();
            int moved = tryInsert(slot.copy(), dest);  // tryInsert calls dest.setChanged()

            if (moved >= count) {
                source.setItem(i, ItemStack.EMPTY);
            } else if (moved > 0) {
                slot.shrink(moved);
                source.setItem(i, slot);
            }

            totalMoved    += moved;
            totalRemaining += (count - moved);
        }

        if (totalMoved > 0) source.setChanged();
        return new int[]{totalMoved, totalRemaining};
    }
}
```

- [ ] **Step 2: Compile**

```
.\gradlew.bat compileJava
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```
git add src/main/java/com/miguealguacil/butler/chest/ChestItemMover.java
git commit -m "feat: add ChestItemMover with tryInsert and tryMoveAll helpers"
```

---

### Task 5: Add all `/butler chest` subcommands to `ButlerCommand`

**Files:**
- Modify: `src/main/java/com/miguealguacil/butler/command/ButlerCommand.java`

- [ ] **Step 1: Add new imports**

Add these after the existing imports in `ButlerCommand.java`:

```java
import com.miguealguacil.butler.chest.ChestEntry;
import com.miguealguacil.butler.chest.ChestItemMover;
import com.miguealguacil.butler.chest.ChestRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
```

> **Note:** This project uses `Identifier` (not `ResourceLocation`) because MC snapshot 26.1.2 renamed that class. If you see a compile error on any `Identifier.*` call, check whether the class is at `net.minecraft.resources.ResourceLocation` and use that instead.

- [ ] **Step 2: Add the `chest` subcommand block to the Brigadier tree**

In `ButlerCommand.register()`, add `.then(Commands.literal("chest")...)` as the last entry before the final `);` that closes `dispatcher.register(...)`. Insert it after the `ask` block:

```java
                .then(Commands.literal("chest")
                    .then(Commands.literal("register")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(ctx -> registerChest(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name")))))
                    .then(Commands.literal("unregister")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(ctx -> unregisterChest(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name")))))
                    .then(Commands.literal("list")
                        .executes(ctx -> listChests(ctx.getSource())))
                    .then(Commands.literal("inspect")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(ctx -> inspectChest(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name")))))
                    .then(Commands.literal("setaccepts")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .then(Commands.argument("items", StringArgumentType.greedyString())
                                .executes(ctx -> setAccepts(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name"),
                                    StringArgumentType.getString(ctx, "items"))))))
                    .then(Commands.literal("move")
                        .then(Commands.argument("from", StringArgumentType.word())
                            .then(Commands.argument("to", StringArgumentType.word())
                                .executes(ctx -> moveChest(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "from"),
                                    StringArgumentType.getString(ctx, "to"))))))
                    .then(Commands.literal("distribute")
                        .then(Commands.argument("src", StringArgumentType.word())
                            .executes(ctx -> distributeChest(ctx.getSource(),
                                StringArgumentType.getString(ctx, "src")))))
                )
```

- [ ] **Step 3: Add `registerChest` private static method**

Add after the closing `}` of `register()`:

```java
private static int registerChest(CommandSourceStack source, String name)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    ServerPlayer player = source.getPlayerOrException();
    ServerLevel level = source.getLevel();
    Vec3 start = player.getEyePosition();
    Vec3 end = start.add(player.getLookAngle().scale(4.5));
    BlockHitResult hit = level.clip(
        new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    if (hit.getType() == HitResult.Type.MISS
            || !(level.getBlockEntity(hit.getBlockPos()) instanceof Container)) {
        source.sendSuccess(() -> Component.literal("[Alfred] No hay ningún cofre ahí."), false);
        return 0;
    }
    if (ChestRegistry.get(name).isPresent()) {
        source.sendSuccess(() -> Component.literal(
            "[Alfred] Ya existe un cofre con el nombre '" + name + "'. Usa unregister primero."), false);
        return 0;
    }
    BlockPos pos = hit.getBlockPos();
    String dim = player.level().dimension().location().toString();
    ChestRegistry.register(new ChestEntry(name, pos.getX(), pos.getY(), pos.getZ(), dim, new ArrayList<>()));
    source.sendSuccess(() -> Component.literal(
        "[Alfred] Cofre registrado como '" + name + "' en " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + "."), false);
    return 1;
}
```

- [ ] **Step 4: Add remaining six private static methods**

Add directly after `registerChest`:

```java
private static int unregisterChest(CommandSourceStack source, String name) {
    if (ChestRegistry.get(name).isEmpty()) {
        source.sendSuccess(() -> Component.literal(
            "[Alfred] No hay ningún cofre registrado con el nombre '" + name + "'."), false);
        return 0;
    }
    ChestRegistry.unregister(name);
    source.sendSuccess(() -> Component.literal("[Alfred] Cofre '" + name + "' eliminado del registro."), false);
    return 1;
}

private static int listChests(CommandSourceStack source) {
    Collection<ChestEntry> all = ChestRegistry.getAll();
    if (all.isEmpty()) {
        source.sendSuccess(() -> Component.literal("[Alfred] No hay cofres registrados."), false);
        return 0;
    }
    for (ChestEntry e : all) {
        source.sendSuccess(() -> Component.literal(
            "- " + e.name() + "  (" + e.x() + ", " + e.y() + ", " + e.z() + ")  "
            + e.dimension() + "  acepta: " + e.accepts().size() + " items"), false);
    }
    return 1;
}

private static int inspectChest(CommandSourceStack source, String name) {
    Optional<ChestEntry> opt = ChestRegistry.get(name);
    if (opt.isEmpty()) {
        source.sendSuccess(() -> Component.literal("[Alfred] Cofre '" + name + "' no registrado."), false);
        return 0;
    }
    ChestEntry entry = opt.get();
    ServerLevel level = source.getServer().getLevel(
        ResourceKey.create(Registries.DIMENSION, Identifier.parse(entry.dimension())));
    if (level == null) {
        source.sendSuccess(() -> Component.literal("[Alfred] Dimensión no disponible."), false);
        return 0;
    }
    BlockEntity be = level.getBlockEntity(entry.blockPos());
    if (!(be instanceof Container container)) {
        source.sendSuccess(() -> Component.literal("[Alfred] No hay cofre en esa posición."), false);
        return 0;
    }
    boolean empty = true;
    for (int i = 0; i < container.getContainerSize(); i++) {
        ItemStack stack = container.getItem(i);
        if (!stack.isEmpty()) {
            empty = false;
            final int slot = i;
            final String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            final int count = stack.getCount();
            source.sendSuccess(() -> Component.literal(
                "[slot " + slot + "] " + itemId + " x" + count), false);
        }
    }
    if (empty)
        source.sendSuccess(() -> Component.literal("[Alfred] El cofre '" + name + "' está vacío."), false);
    return 1;
}

private static int setAccepts(CommandSourceStack source, String name, String itemsStr) {
    if (ChestRegistry.get(name).isEmpty()) {
        source.sendSuccess(() -> Component.literal("[Alfred] Cofre '" + name + "' no registrado."), false);
        return 0;
    }
    List<String> items = Arrays.asList(itemsStr.trim().split("\\s+"));
    ChestRegistry.setAccepts(name, items);
    source.sendSuccess(() -> Component.literal(
        "[Alfred] Cofre '" + name + "' ahora acepta " + items.size() + " tipos de items."), false);
    return 1;
}

private static int moveChest(CommandSourceStack source, String from, String to) {
    Optional<ChestEntry> fromOpt = ChestRegistry.get(from);
    Optional<ChestEntry> toOpt   = ChestRegistry.get(to);
    if (fromOpt.isEmpty()) {
        source.sendSuccess(() -> Component.literal("[Alfred] Cofre '" + from + "' no registrado."), false); return 0;
    }
    if (toOpt.isEmpty()) {
        source.sendSuccess(() -> Component.literal("[Alfred] Cofre '" + to + "' no registrado."), false); return 0;
    }
    ServerLevel fromLevel = source.getServer().getLevel(
        ResourceKey.create(Registries.DIMENSION, Identifier.parse(fromOpt.get().dimension())));
    ServerLevel toLevel = source.getServer().getLevel(
        ResourceKey.create(Registries.DIMENSION, Identifier.parse(toOpt.get().dimension())));
    if (fromLevel == null || toLevel == null) {
        source.sendSuccess(() -> Component.literal("[Alfred] Dimensión no disponible."), false); return 0;
    }
    BlockEntity fromBe = fromLevel.getBlockEntity(fromOpt.get().blockPos());
    BlockEntity toBe   = toLevel.getBlockEntity(toOpt.get().blockPos());
    if (!(fromBe instanceof Container fromCont)) {
        source.sendSuccess(() -> Component.literal("[Alfred] No hay cofre en '" + from + "'."), false); return 0;
    }
    if (!(toBe instanceof Container toCont)) {
        source.sendSuccess(() -> Component.literal("[Alfred] No hay cofre en '" + to + "'."), false); return 0;
    }
    int[] result = ChestItemMover.tryMoveAll(fromCont, toCont);
    final int moved = result[0], remaining = result[1];
    source.sendSuccess(() -> Component.literal(
        "[Alfred] Movidos " + moved + " items de '" + from + "' a '" + to + "'."), false);
    if (remaining > 0)
        source.sendSuccess(() -> Component.literal(
            "[Alfred] El cofre '" + to + "' está lleno. Quedan " + remaining + " items sin colocar."), false);
    return 1;
}

private static int distributeChest(CommandSourceStack source, String srcName) {
    Optional<ChestEntry> srcOpt = ChestRegistry.get(srcName);
    if (srcOpt.isEmpty()) {
        source.sendSuccess(() -> Component.literal("[Alfred] Cofre '" + srcName + "' no registrado."), false);
        return 0;
    }
    ChestEntry srcEntry = srcOpt.get();
    ServerLevel srcLevel = source.getServer().getLevel(
        ResourceKey.create(Registries.DIMENSION, Identifier.parse(srcEntry.dimension())));
    if (srcLevel == null) {
        source.sendSuccess(() -> Component.literal("[Alfred] Dimensión no disponible."), false); return 0;
    }
    BlockEntity srcBe = srcLevel.getBlockEntity(srcEntry.blockPos());
    if (!(srcBe instanceof Container srcCont)) {
        source.sendSuccess(() -> Component.literal("[Alfred] No hay cofre en '" + srcName + "'."), false); return 0;
    }

    int totalMoved = 0, unassigned = 0, didNotFit = 0;

    for (int i = 0; i < srcCont.getContainerSize(); i++) {
        ItemStack slot = srcCont.getItem(i);
        if (slot.isEmpty()) continue;

        String itemId = BuiltInRegistries.ITEM.getKey(slot.getItem()).toString();

        Optional<ChestEntry> destOpt = ChestRegistry.getAll().stream()
            .filter(e -> !e.name().equals(srcName) && e.accepts().contains(itemId))
            .findFirst();

        if (destOpt.isEmpty()) { unassigned += slot.getCount(); continue; }

        ChestEntry destEntry = destOpt.get();
        ServerLevel destLevel = source.getServer().getLevel(
            ResourceKey.create(Registries.DIMENSION, Identifier.parse(destEntry.dimension())));
        if (destLevel == null) { unassigned += slot.getCount(); continue; }
        BlockEntity destBe = destLevel.getBlockEntity(destEntry.blockPos());
        if (!(destBe instanceof Container destCont)) { unassigned += slot.getCount(); continue; }

        int count = slot.getCount();
        int moved = ChestItemMover.tryInsert(slot.copy(), destCont);

        if (moved >= count) {
            srcCont.setItem(i, ItemStack.EMPTY);
        } else if (moved > 0) {
            slot.shrink(moved);
            srcCont.setItem(i, slot);
            didNotFit += (count - moved);
        } else {
            didNotFit += count;
        }
        totalMoved += moved;
    }

    if (totalMoved > 0) srcCont.setChanged();
    final int fm = totalMoved, fu = unassigned, fd = didNotFit;
    source.sendSuccess(() -> Component.literal(
        "[Alfred] Distribuidos " + fm + " items. " + fu + " sin asignar. " + fd + " no cupieron en destino."), false);
    return 1;
}
```

- [ ] **Step 5: Compile**

```
.\gradlew.bat compileJava
```
Expected: `BUILD SUCCESSFUL`

If you see `cannot find symbol: class Identifier`, replace all `Identifier.parse(...)` with `net.minecraft.resources.ResourceLocation.parse(...)` — in some MC mappings the class is still `ResourceLocation`.

- [ ] **Step 6: In-game verification**

Run `.\gradlew.bat runClient`, open a world (cheats on), place two chests with items.

```
# Register first chest (look at it before typing)
/butler chest register almacen
→ [Alfred] Cofre registrado como 'almacen' en X Y Z.

/butler chest list
→ - almacen  (X, Y, Z)  minecraft:overworld  acepta: 0 items

/butler chest inspect almacen
→ [slot 0] minecraft:cobblestone x32
→ [slot 1] minecraft:dirt x10
  (etc. for each non-empty slot)

/butler chest setaccepts almacen minecraft:cobblestone minecraft:dirt
→ [Alfred] Cofre 'almacen' ahora acepta 2 tipos de items.

# Register second chest (look at it)
/butler chest register deposito
→ [Alfred] Cofre registrado como 'deposito' en X Y Z.

/butler chest move almacen deposito
→ [Alfred] Movidos N items de 'almacen' a 'deposito'.

# Restart the world — both chests must still appear
/butler chest list
→ - almacen  ...
→ - deposito  ...

# Set up distribute: two chests with accepts lists, one source
/butler chest setaccepts armeria minecraft:iron_sword
/butler chest distribute almacen
→ [Alfred] Distribuidos N items. M sin asignar. K no cupieron en destino.
```

- [ ] **Step 7: Commit**

```
git add src/main/java/com/miguealguacil/butler/command/ButlerCommand.java
git commit -m "feat: add /butler chest register/list/inspect/setaccepts/move/distribute commands"
```

---

### Task 6: Create backend mockup (Python)

**Files:**
- Create: `app/features/butler/__init__.py`
- Create: `app/features/butler/schemas.py`
- Create: `app/features/butler/chest_router.py`
- Modify: `app/main.py`

> Java does NOT call these endpoints in Phase 5. They exist for Phase 6 LLM integration. The user will complete the implementation manually.

- [ ] **Step 1: Create `app/features/butler/__init__.py`**

```python
```
(empty file)

- [ ] **Step 2: Create `app/features/butler/schemas.py`**

```python
from pydantic import BaseModel


class ButlerAction(BaseModel):
    type: str
    message: str
    x: int | None = None
    y: int | None = None
    z: int | None = None
```

- [ ] **Step 3: Create `app/features/butler/chest_router.py`**

```python
from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.features.auth.dependencies import get_authenticated_user
from app.features.butler.schemas import ButlerAction
from app.features.users.models import User

router = APIRouter(prefix="/chest", tags=["butler-chest"])


class ChestItemEntry(BaseModel):
    slot: int
    item: str    # fully-qualified Minecraft item ID, e.g. "minecraft:iron_sword"
    count: int


class InspectRequest(BaseModel):
    chest_name: str
    contents: list[ChestItemEntry]


class DistributePlanRequest(BaseModel):
    source: str
    items: list[ChestItemEntry]  # items currently in the source chest


@router.post("/inspect", response_model=list[ButlerAction])
async def inspect_chest(
    req: InspectRequest,
    _user: Annotated[User, Depends(get_authenticated_user)],
) -> list[ButlerAction]:
    """
    Called by Java after reading a chest's contents.

    PHASE 5 (current): Returns a summary speak action.

    PHASE 6 TODO:
        1. Query database for the full chest registry (names + accept lists).
        2. Build an LLM prompt: "Given these chest contents and these registered chests
           with their accept lists, which items should be moved where?"
        3. Parse the LLM JSON response into ButlerAction objects:
           - type="speak" for a human-readable summary
           - type="move_items" (new Phase 6 action) for each suggested move:
             {"type": "move_items", "from_chest": "almacen", "to_chest": "armeria",
              "item": "minecraft:iron_sword", "count": 3}
        4. Return the list; Java executes each action via ButlerActionExecutor.
    """
    count = len(req.contents)
    return [
        ButlerAction(
            type="speak",
            message=f"He inspeccionado '{req.chest_name}'. Contiene {count} tipos de ítems.",
        )
    ]


@router.post("/distribute_plan", response_model=list[ButlerAction])
async def distribute_plan(
    req: DistributePlanRequest,
    _user: Annotated[User, Depends(get_authenticated_user)],
) -> list[ButlerAction]:
    """
    Called by Java before distributing items from a source chest.

    PHASE 5 (current): Returns an empty list.
    Java handles distribution autonomously using butler_chests.json accept lists.

    PHASE 6 TODO:
        1. Query database for the full chest registry.
        2. Build an LLM prompt: "Given these items and these destination chests
           with their accept lists, assign each item to the best destination."
        3. Parse the LLM JSON response into move_items ButlerAction objects:
           [{"type": "move_items", "from_chest": "cofre_central",
             "to_chest": "armeria", "item": "minecraft:iron_sword", "count": 3}, ...]
        4. Return the list; Java executes each move_items action, replacing the
           local accept-list logic in distributeChest().
    """
    return []
```

- [ ] **Step 4: Register the chest router in `app/main.py`**

Add the import alongside the other router imports:
```python
from app.features.butler.chest_router import router as chest_router
```

Add the `include_router` call before `app.include_router(spa_router)`:
```python
app.include_router(chest_router, prefix="/api/butler")
```

- [ ] **Step 5: Commit**

```
git add app/features/butler/__init__.py app/features/butler/schemas.py
git add app/features/butler/chest_router.py app/main.py
git commit -m "feat: add Phase 5 butler chest router mockup with Phase 6 LLM documentation"
```

---

## Verification Checklist

| Test | Expected |
|------|---------|
| `/butler chest register almacen` (looking at chest) | `[Alfred] Cofre registrado como 'almacen'…` |
| Restart world, `/butler chest list` | `almacen` still listed |
| `/butler chest inspect almacen` | Lists each non-empty slot |
| `/butler chest setaccepts armeria minecraft:iron_sword` | `acepta 1 tipos de items` |
| `/butler chest move almacen deposito` | Items moved; full warning if needed |
| `/butler chest distribute almacen` | Items routed; summary counts shown |
| `/butler ping`, `/butler spawn`, `/butler follow`, `/butler stop` | No regressions |
