# Movement Placeholder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `move_to_position` action to the butler pipeline — the backend detects coordinate triples in the player's message and returns a typed action; the Java executor displays a placeholder text response.

**Architecture:** The backend's `/api/butler/ask` router gains a regex check (`(-?\d+)\s+(-?\d+)\s+(-?\d+)`) over the incoming message. If coordinates are found it returns `move_to_position`; otherwise `speak` as before. `ButlerAction` (Python and Java) gains optional `x`, `y`, `z` fields. `ButlerActionExecutor` gains a new case that formats a chat message. No entity spawning, no pathfinding.

**Tech Stack:** Python 3.14 / FastAPI / Pydantic v2 / pytest (backend) · Java 25 / Fabric / Brigadier / Gson (mod)

---

## Working directories

| Project | Path |
|---------|------|
| Backend | `C:\Users\migue\Documents\Proyectos\MinecraftButlerAI Backend` |
| Mod | `C:\Users\migue\Documents\Proyectos\MinecraftButlerAI` |

---

## File Map

| Action | File | Change |
|--------|------|--------|
| Modify | `app/features/butler/schemas.py` | Add `x`, `y`, `z: int \| None = None` to `ButlerAction` |
| Modify | `app/features/butler/router.py` | Add `_COORD_RE` regex + coordinate branch; add `response_model_exclude_none=True` |
| Modify | `tests/test_api.py` | Add 2 butler coordinate tests |
| Modify | `src/main/java/com/miguealguacil/butler/action/ButlerAction.java` | Add `Integer x, y, z` to record |
| Modify | `src/main/java/com/miguealguacil/butler/http/ButlerHttpClient.java` | Parse `x`, `y`, `z` from JSON (null-safe) |
| Modify | `src/main/java/com/miguealguacil/butler/action/ButlerActionExecutor.java` | Add `move_to_position` case |
| Modify | `src/main/java/com/miguealguacil/butler/command/ButlerCommand.java` | Update mock to 5-field constructor |

---

## Task 1 — Backend: write failing test (TDD red)

**Files:**
- Modify: `tests/test_api.py`

All backend commands run from `C:\Users\migue\Documents\Proyectos\MinecraftButlerAI Backend`.

- [ ] **Step 1: Append the two new tests to `tests/test_api.py`**

Add at the end of the file (after the existing butler tests):

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

- [ ] **Step 2: Run the coordinate test — must fail**

```powershell
.venv\Scripts\python.exe -m pytest tests/test_api.py::test_ask_with_coordinates_returns_move_to_position -v
```

Expected failure:
```
FAILED tests/test_api.py::test_ask_with_coordinates_returns_move_to_position
AssertionError: assert 'speak' == 'move_to_position'
```

If it passes, something is wrong — stop and investigate before continuing.

---

## Task 2 — Backend: implement schema + router (TDD green)

**Files:**
- Modify: `app/features/butler/schemas.py`
- Modify: `app/features/butler/router.py`

- [ ] **Step 1: Update `app/features/butler/schemas.py`**

Replace the existing file contents with:

```python
from pydantic import BaseModel


class AskRequest(BaseModel):
    message: str


class ButlerAction(BaseModel):
    type: str
    message: str
    x: int | None = None
    y: int | None = None
    z: int | None = None
```

- [ ] **Step 2: Replace `app/features/butler/router.py`**

```python
import re

from fastapi import APIRouter, Depends

from app.features.auth.dependencies import get_authenticated_user
from app.features.butler.schemas import AskRequest, ButlerAction
from app.features.users.models import User

router = APIRouter(prefix="/api/butler", tags=["Butler"])

_COORD_RE = re.compile(r"(-?\d+)\s+(-?\d+)\s+(-?\d+)")


@router.post("/ask", response_model=list[ButlerAction], response_model_exclude_none=True)
async def ask(req: AskRequest, _user: User = Depends(get_authenticated_user)) -> list[ButlerAction]:
    match = _COORD_RE.search(req.message)
    if match:
        x, y, z = int(match.group(1)), int(match.group(2)), int(match.group(3))
        return [ButlerAction(type="move_to_position", message="Me dirijo allí.", x=x, y=y, z=z)]
    return [ButlerAction(type="speak", message=f"Hola! Recibí: {req.message}")]
```

- [ ] **Step 3: Run all butler tests**

```powershell
.venv\Scripts\python.exe -m pytest tests/test_api.py::test_ask_without_auth tests/test_api.py::test_ask_with_valid_token tests/test_api.py::test_ask_response_is_list tests/test_api.py::test_ask_with_coordinates_returns_move_to_position tests/test_api.py::test_ask_without_coordinates_returns_speak -v
```

Expected:
```
PASSED tests/test_api.py::test_ask_without_auth
PASSED tests/test_api.py::test_ask_with_valid_token
PASSED tests/test_api.py::test_ask_response_is_list
PASSED tests/test_api.py::test_ask_with_coordinates_returns_move_to_position
PASSED tests/test_api.py::test_ask_without_coordinates_returns_speak
5 passed
```

If any test fails, check the error message and fix before continuing.

- [ ] **Step 4: Commit**

```powershell
git add app/features/butler/schemas.py app/features/butler/router.py tests/test_api.py
git commit -m "feat: add move_to_position action with coordinate detection"
```

---

## Task 3 — Java: extend ButlerAction and update all consumers

**Files:**
- Modify: `src/main/java/com/miguealguacil/butler/action/ButlerAction.java`
- Modify: `src/main/java/com/miguealguacil/butler/http/ButlerHttpClient.java`
- Modify: `src/main/java/com/miguealguacil/butler/action/ButlerActionExecutor.java`
- Modify: `src/main/java/com/miguealguacil/butler/command/ButlerCommand.java`

All commands from `C:\Users\migue\Documents\Proyectos\MinecraftButlerAI`.

Do all four edits before compiling — the record change breaks callers, which only resolve together.

- [ ] **Step 1: Replace `ButlerAction.java`**

```java
package com.miguealguacil.butler.action;

public record ButlerAction(String type, String message, Integer x, Integer y, Integer z) {}
```

- [ ] **Step 2: Update `askAsync` in `ButlerHttpClient.java`**

Find the block that calls `actions.add(new ButlerAction(...))` inside `askAsync` and replace it:

```java
                    List<ButlerAction> actions = new ArrayList<>();
                    for (JsonElement el : GSON.fromJson(resp.body(), JsonArray.class)) {
                        JsonObject obj = el.getAsJsonObject();
                        actions.add(new ButlerAction(
                                obj.get("type").getAsString(),
                                obj.get("message").getAsString(),
                                obj.has("x") && !obj.get("x").isJsonNull() ? obj.get("x").getAsInt() : null,
                                obj.has("y") && !obj.get("y").isJsonNull() ? obj.get("y").getAsInt() : null,
                                obj.has("z") && !obj.get("z").isJsonNull() ? obj.get("z").getAsInt() : null));
                    }
                    return actions;
```

- [ ] **Step 3: Replace `ButlerActionExecutor.java`**

```java
package com.miguealguacil.butler.action;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class ButlerActionExecutor {
    private ButlerActionExecutor() {}

    public static void execute(ButlerAction action, CommandSourceStack source) {
        switch (action.type()) {
            case "speak" ->
                source.sendSuccess(() -> Component.literal("[Alfred] " + action.message()), false);
            case "move_to_position" -> {
                if (action.x() != null && action.y() != null && action.z() != null) {
                    source.sendSuccess(() -> Component.literal(
                        "[Alfred] Me dirijo a " + action.x() + " " + action.y() + " " + action.z() + "."), false);
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

- [ ] **Step 4: Update the mock in `ButlerCommand.java`**

Find the line inside the `mock` branch:

```java
new ButlerAction("speak", "He recibido una acción mock correctamente."),
```

Replace with:

```java
new ButlerAction("speak", "He recibido una acción mock correctamente.", null, null, null),
```

- [ ] **Step 5: Compile**

```powershell
.\gradlew.bat compileJava
```

Expected: `BUILD SUCCESSFUL`

Common failures:
- `cannot find symbol` on `ButlerAction` constructor → one of the four files still uses the old 2-argument constructor. Search the project for `new ButlerAction(` and fix.
- `obj.has` not found → check the Gson import `com.google.gson.JsonObject` is present in `ButlerHttpClient.java`.

- [ ] **Step 6: Full build**

```powershell
.\gradlew.bat build
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/miguealguacil/butler/action/ButlerAction.java `
       src/main/java/com/miguealguacil/butler/http/ButlerHttpClient.java `
       src/main/java/com/miguealguacil/butler/action/ButlerActionExecutor.java `
       src/main/java/com/miguealguacil/butler/command/ButlerCommand.java
git commit -m "feat: extend ButlerAction with coordinates and add move_to_position executor"
```

---

## Task 4 — Integration test in-game

- [ ] **Step 1: Start the backend (separate terminal)**

```powershell
cd "C:\Users\migue\Documents\Proyectos\MinecraftButlerAI Backend"
.venv\Scripts\Activate.ps1
uvicorn app.main:app --reload --port 8000
```

- [ ] **Step 2: Launch Minecraft**

```powershell
cd "C:\Users\migue\Documents\Proyectos\MinecraftButlerAI"
.\gradlew.bat runClient
```

- [ ] **Step 3: Test movement action**

Open a singleplayer world with cheats. Run:

```
/butler ask ve a 100 64 -50
```

Expected (two messages):
```
[Alfred] Procesando...
[Alfred] Me dirijo a 100 64 -50.
```

- [ ] **Step 4: Test speak action still works**

```
/butler ask hola Alfred
```

Expected:
```
[Alfred] Procesando...
[Alfred] Hola! Recibí: hola Alfred
```

- [ ] **Step 5: Verify mock is not broken**

```
/butler mock
```

Expected:
```
[Alfred] He recibido una acción mock correctamente.
```

- [ ] **Step 6: Final commit**

```powershell
git add -A
git commit -m "chore: verify Phase 3 move_to_position works in-game"
```

---

## Command reference

| Purpose | Command |
|---------|---------|
| Backend tests | `.venv\Scripts\python.exe -m pytest tests/test_api.py -v` |
| Compile mod | `.\gradlew.bat compileJava` |
| Full build | `.\gradlew.bat build` |
| Launch game | `.\gradlew.bat runClient` |
| Start backend | `uvicorn app.main:app --reload --port 8000` |
