# Phase 5 — Chest Inventory Design

**Date:** 2026-05-26
**Status:** Approved

---

## Goal

Add chest registry, inspection, item movement, and distribution to the butler pipeline. Phase 5 proves that Java can read and write Minecraft container inventories, persist named chest aliases across sessions, and distribute items autonomously based on per-chest accept lists. The backend is stubbed with documented endpoints ready for Phase 6 LLM integration.

---

## Scope Boundaries

In Phase 5:
- Named chest registry with JSON persistence
- `/butler chest register/unregister/list/inspect/setaccepts/move/distribute`
- Item movement between registered chests
- Distribution by per-chest accept lists
- Backend stub endpoints with Phase 6 documentation

Not in Phase 5:
- Backend calling any LLM
- Java calling the backend (no HTTP from chest commands)
- Chest contents persisted in world NBT
- Multi-chest double-chest handling (treat as single Container)
- Undo / transaction rollback
- Item tag matching (e.g. `#minecraft:swords`) — only exact item IDs

---

## Data Model

### `ChestEntry` (Java record)

```java
public record ChestEntry(
    String name,
    int x, int y, int z,
    String dimension,
    List<String> accepts
) {
    public BlockPos blockPos() { return new BlockPos(x, y, z); }
}
```

`accepts` is a list of fully-qualified item IDs (e.g. `"minecraft:iron_sword"`). Empty list means the chest is not a distribution target.

### `butler_chests.json` (in `run/`)

```json
{
  "chests": [
    {
      "name": "armería",
      "x": 10, "y": 64, "z": -5,
      "dimension": "minecraft:overworld",
      "accepts": ["minecraft:iron_sword", "minecraft:bow", "minecraft:arrow"]
    },
    {
      "name": "granja",
      "x": 20, "y": 64, "z": -5,
      "dimension": "minecraft:overworld",
      "accepts": ["minecraft:wheat", "minecraft:carrot", "minecraft:potato"]
    }
  ]
}
```

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `src/main/java/com/miguealguacil/butler/chest/ChestEntry.java` | Data record |
| Create | `src/main/java/com/miguealguacil/butler/chest/ChestRegistry.java` | In-memory cache + load/save JSON |
| Modify | `src/main/java/com/miguealguacil/butler/command/ButlerCommand.java` | Add `chest` subcommand tree |
| Modify | `src/main/java/com/miguealguacil/butler/AIButler.java` | Register `SERVER_STARTED` to load registry |
| Create (mockup) | `app/features/butler/chest_router.py` | Stub endpoints with Phase 6 docs |
| Modify (mockup) | `app/main.py` | Register chest router |

---

## ChestRegistry

```java
public final class ChestRegistry {
    private static final Path FILE = Path.of("butler_chests.json");
    private static final Map<String, ChestEntry> registry = new LinkedHashMap<>();

    private ChestRegistry() {}

    public static void load() { /* read FILE with Gson, populate registry */ }
    public static void save() { /* write registry to FILE with Gson pretty-print */ }

    public static void register(ChestEntry entry) { registry.put(entry.name(), entry); save(); }
    public static void unregister(String name)    { registry.remove(name); save(); }
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

`load()` is called from `AIButler.onInitialize()` via `ServerLifecycleEvents.SERVER_STARTED`. If the file does not exist, the registry starts empty.

---

## Commands

All subcommands live under `/butler chest <sub>`. They all require a player source (`getPlayerOrException()`).

### `/butler chest register <name>`

1. `player.pick(4.5, 0f, false)` → cast to `BlockHitResult`.
2. `level.getBlockEntity(blockPos) instanceof Container` — if not: `[Alfred] No hay ningún cofre ahí.`
3. If name already taken: `[Alfred] Ya existe un cofre con el nombre 'X'. Usa unregister primero.`
4. Create `ChestEntry` with player's current dimension: `player.level().dimension().location().toString()`.
5. `ChestRegistry.register(entry)` → `[Alfred] Cofre registrado como 'X' en X Y Z.`

### `/butler chest unregister <name>`

1. If not found: `[Alfred] No hay ningún cofre registrado con el nombre 'X'.`
2. `ChestRegistry.unregister(name)` → `[Alfred] Cofre 'X' eliminado del registro.`

### `/butler chest list`

1. If empty: `[Alfred] No hay cofres registrados.`
2. One line per entry: `- armería  (10, 64, -5)  overworld  acepta: 3 items`

### `/butler chest inspect <name>`

1. Resolve `ChestEntry` — if missing: `[Alfred] Cofre 'X' no registrado.`
2. `level.getBlockEntity(pos) instanceof Container` — if missing: `[Alfred] No hay cofre en esa posición.`
3. Iterate slots; collect non-empty stacks.
4. If empty: `[Alfred] El cofre 'X' está vacío.`
5. Otherwise list: `[slot N] minecraft:iron_sword x3`, one line per non-empty slot.

### `/butler chest setaccepts <name> <items…>`

Argument: `StringArgumentType.greedyString()` — split on whitespace.

1. Resolve entry.
2. `ChestRegistry.setAccepts(name, itemIds)` → `[Alfred] Cofre 'X' ahora acepta N tipos de items.`

### `/butler chest move <from> <to>`

1. Resolve both entries.
2. Get `Container` for each; fail clearly if either block is missing.
3. Call `tryMoveAll(source, dest)` → returns `(moved, remaining)`.
4. `[Alfred] Movidos N items de 'from' a 'to'.`
5. If `remaining > 0`: `[Alfred] El cofre 'to' está lleno. Quedan M items sin colocar.`

### `/butler chest distribute <source>`

1. Resolve source entry and get its `Container`.
2. For each non-empty slot in source:
   - Get item ID string.
   - Find first registered chest whose `accepts` contains that ID.
   - Call `tryInsert(stack, destContainer)` → items moved.
   - Accumulate: `totalMoved`, `unassigned` (no chest accepts it), `didNotFit`.
3. `[Alfred] Distribuidos N items. M sin asignar. K no cupieron en destino.`

---

## Item Movement Helpers

```java
// Returns number of items successfully inserted into dest.
static int tryInsert(ItemStack stack, Container dest)

// Moves as much of each source slot as possible into dest.
// Returns int[]{totalMoved, totalRemaining}
static int[] tryMoveAll(Container source, Container dest)
```

`tryInsert` logic:
1. First pass: merge with existing matching stacks (respect `getMaxStackSize()`).
2. Second pass: fill empty slots.
3. Reduce `stack.getCount()` by inserted amount; return inserted count.

---

## Backend Mockup

### `app/features/butler/chest_router.py`

```python
router = APIRouter(prefix="/chest", tags=["chest"])

class ChestItemEntry(BaseModel):
    slot: int
    item: str
    count: int

class InspectRequest(BaseModel):
    chest_name: str
    contents: list[ChestItemEntry]

class DistributePlanRequest(BaseModel):
    source: str
    items: list[dict]  # {"item": str, "count": int}

@router.post("/inspect", response_model=list[ButlerAction])
async def inspect_chest(req: InspectRequest, ...) -> list[ButlerAction]:
    # MOCKUP: echo a summary speak action.
    # Phase 6: pass req.contents to LLM with registered chests context.
    # LLM should categorize items and suggest which registered chest each belongs to.
    # Return list of move_items actions or a speak action with suggestions.
    count = len(req.contents)
    return [ButlerAction(type="speak",
                         message=f"He inspeccionado '{req.chest_name}'. Contiene {count} tipos de ítems.")]

@router.post("/distribute_plan", response_model=list[ButlerAction])
async def distribute_plan(req: DistributePlanRequest, ...) -> list[ButlerAction]:
    # MOCKUP: returns empty list — Java handles distribution autonomously in Phase 5.
    # Phase 6: pass req.items and the full chest registry to LLM.
    # LLM returns suggested distribution as move_items actions:
    #   [{"type": "move_items", "from": "cofre_central", "to": "armería", "item": "minecraft:iron_sword", "count": 3}]
    # Java then executes each move_items action via ButlerActionExecutor.
    return []
```

Both endpoints require authentication (`Depends(get_authenticated_user)`).
Register router in `app/main.py` under `/api/butler`.

---

## Verification (in-game)

| Command | Expected result |
|---------|-----------------|
| `/butler chest register almacen` (looking at chest) | `[Alfred] Cofre registrado como 'almacen' en X Y Z.` |
| Restart world | `almacen` still listed in `/butler chest list` |
| `/butler chest inspect almacen` | Lists each non-empty slot |
| `/butler chest setaccepts armería minecraft:iron_sword` | `[Alfred] Cofre 'armería' ahora acepta 1 tipos de items.` |
| `/butler chest move almacen armería` | Items moved; full-chest warning if applicable |
| `/butler chest distribute almacen` | Items routed to matching chests; unassigned items reported |
| `/butler ping`, `/butler spawn`, `/butler follow` | No regressions |
