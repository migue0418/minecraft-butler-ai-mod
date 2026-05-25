# Phase 3 — Movement Placeholder Design

**Date:** 2026-05-25
**Status:** Approved

---

## Goal

Add a `move_to_position` action to the butler pipeline. No custom NPC entity yet — Phase 3 is purely about proving that coordinate data flows correctly from the backend to the Java executor. The placeholder behavior is a text message to the player.

---

## Data Contract

`ButlerAction` gains three optional coordinate fields. The JSON shape:

```json
// Movement action:
{"type": "move_to_position", "message": "Me dirijo allí.", "x": 100, "y": 64, "z": -50}

// Speak action (unchanged — x/y/z absent):
{"type": "speak", "message": "Hola! Recibí: hola Alfred"}
```

`x`, `y`, `z` are absent for non-movement actions. The Java side reads them with null-checks.

---

## Backend Changes (`MinecraftButlerAI Backend`)

### `app/features/butler/schemas.py`

Add optional coordinate fields to `ButlerAction`:

```python
class ButlerAction(BaseModel):
    type: str
    message: str
    x: int | None = None
    y: int | None = None
    z: int | None = None
```

`AskRequest` is unchanged.

### `app/features/butler/router.py`

Add regex coordinate detection. Pattern `(-?\d+)\s+(-?\d+)\s+(-?\d+)` matches three consecutive space-separated integers with optional negative sign.

```python
import re

_COORD_RE = re.compile(r"(-?\d+)\s+(-?\d+)\s+(-?\d+)")

@router.post("/ask", response_model=list[ButlerAction])
async def ask(req: AskRequest, _user: User = Depends(get_authenticated_user)) -> list[ButlerAction]:
    match = _COORD_RE.search(req.message)
    if match:
        x, y, z = int(match.group(1)), int(match.group(2)), int(match.group(3))
        return [ButlerAction(type="move_to_position", message="Me dirijo allí.", x=x, y=y, z=z)]
    return [ButlerAction(type="speak", message=f"Hola! Recibí: {req.message}")]
```

---

## Java Mod Changes (`MinecraftButlerAI`)

### `action/ButlerAction.java`

Extend the record with three nullable coordinate fields:

```java
public record ButlerAction(String type, String message, Integer x, Integer y, Integer z) {}
```

### `http/ButlerHttpClient.java` — `askAsync`

Update JSON deserialization to read optional x, y, z:

```java
actions.add(new ButlerAction(
    obj.get("type").getAsString(),
    obj.get("message").getAsString(),
    obj.has("x") && !obj.get("x").isJsonNull() ? obj.get("x").getAsInt() : null,
    obj.has("y") && !obj.get("y").isJsonNull() ? obj.get("y").getAsInt() : null,
    obj.has("z") && !obj.get("z").isJsonNull() ? obj.get("z").getAsInt() : null));
```

### `action/ButlerActionExecutor.java`

Add `move_to_position` case:

```java
case "move_to_position" -> {
    if (action.x() != null && action.y() != null && action.z() != null) {
        source.sendSuccess(() -> Component.literal(
            "[Alfred] Me dirijo a " + action.x() + " " + action.y() + " " + action.z() + "."), false);
    } else {
        source.sendSuccess(() -> Component.literal("[Alfred] Destino no especificado."), false);
    }
}
```

### `command/ButlerCommand.java` — mock subcommand

Update the mock `ButlerAction` instantiation (record now has 5 fields):

```java
new ButlerAction("speak", "He recibido una acción mock correctamente.", null, null, null)
```

---

## Tests

### Backend (`tests/test_api.py`)

```python
def test_ask_with_coordinates_returns_move_to_position(client: TestClient) -> None:
    tokens = login(client)
    response = client.post(
        "/api/butler/ask",
        json={"message": "ve a 100 64 -50"},
        headers=auth_headers(tokens["access_token"]),
    )
    assert response.status_code == 200
    actions = response.json()
    assert len(actions) == 1
    assert actions[0]["type"] == "move_to_position"
    assert actions[0]["x"] == 100
    assert actions[0]["y"] == 64
    assert actions[0]["z"] == -50


def test_ask_without_coordinates_returns_speak(client: TestClient) -> None:
    tokens = login(client)
    response = client.post(
        "/api/butler/ask",
        json={"message": "hola Alfred"},
        headers=auth_headers(tokens["access_token"]),
    )
    assert response.status_code == 200
    assert response.json()[0]["type"] == "speak"
```

### Java (in-game)

```
/butler ask ve a 100 64 -50
→ [Alfred] Procesando...
→ [Alfred] Me dirijo a 100 64 -50.

/butler ask hola Alfred
→ [Alfred] Procesando...
→ [Alfred] Hola! Recibí: hola Alfred

/butler mock
→ [Alfred] He recibido una acción mock correctamente.
```

---

## Scope Boundaries

Not in Phase 3:
- Custom NPC entity or model
- Actual pathfinding or entity movement
- Persisting the target position (Phase 4 may use `ButlerState` for this)
- Natural language parsing of destination names ("ve a casa")
