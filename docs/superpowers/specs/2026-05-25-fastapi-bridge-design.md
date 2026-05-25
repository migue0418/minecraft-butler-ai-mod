# Phase 2 — FastAPI Bridge Design

**Date:** 2026-05-25
**Status:** Approved

---

## Goal

Add `/butler ask <message>` to the Minecraft mod. The command sends the player's message to a separate FastAPI backend over HTTP, receives an ordered list of actions, and executes them. Phase 2 executes only the `speak` action. The FastAPI backend is protected by JWT bearer authentication.

---

## Projects

| Project | Language | Repo |
|---------|----------|------|
| MinecraftButlerAI | Java 25 / Fabric | existing (this repo) |
| MinecraftButlerAI Backend | Python / FastAPI | separate repo |

---

## Architecture

```
Minecraft mod (Java)                     FastAPI backend (Python)
─────────────────────────────────        ────────────────────────
ButlerCommand  ──ask──▶  ButlerHttpClient  ──POST /login──▶  /login
                              │            ◀── {access_token} ──
                              │
                              └──────────  ──POST /ask ──▶  /ask (JWT protected)
                                           ◀── [ButlerAction] ──
                              │
                         server.execute()
                              │
                         ButlerActionExecutor
                              │
                         player sees message
```

---

## HTTP Contract

### POST /login

**Request:**
```json
{ "username": "string", "password": "string" }
```

**Response 200:**
```json
{ "access_token": "string", "token_type": "bearer" }
```

**Response 401:**
```json
{ "detail": "Credenciales inválidas." }
```

### POST /ask

**Headers:**
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

**Request:**
```json
{ "message": "string" }
```

**Response 200:**
```json
[{ "type": "speak", "message": "string" }]
```
The response is an array to support multi-action plans in future phases without changing the contract.

**Response 401:** Token missing or invalid — mod re-logs in once and retries.

---

## Java Mod Changes

### New files

#### `http/ButlerHttpClient.java`

Responsibilities:
- Holds credentials as constants (`USERNAME = "admin"`, `PASSWORD = "admin"`)
- Holds `BASE_URL = "http://localhost:8000"` as a constant
- Caches the JWT token as a static field (`String cachedToken`)
- `loginAsync()` → `CompletableFuture<String>`: POSTs to `/login`, stores and returns token
- `sendAsync(String message)` → `CompletableFuture<List<ButlerAction>>`:
  1. If no cached token, calls `loginAsync()` first
  2. POSTs to `/ask` with `Authorization: Bearer <token>`
  3. If response is 401, clears token, re-logs in once, retries `/ask`
  4. Deserializes JSON array with Gson (bundled with Minecraft) into `List<ButlerAction>`

Uses `java.net.http.HttpClient` (Java 11+, no new dependencies).
Uses `com.google.gson.Gson` (bundled with Minecraft, no new dependencies).

### Modified files

#### `command/ButlerCommand.java`

Add new subcommand:

```
/butler ask <message: greedy string>
```

- Captures `CommandSourceStack source` and `MinecraftServer server` from the server thread
- Sends `[Alfred] Procesando...` immediately so the player gets feedback
- Calls `ButlerHttpClient.sendAsync(message)`
- In the `CompletableFuture` callback (HTTP thread), calls `server.execute(() -> ...)` to run actions on the server thread
- On success: calls `ButlerActionExecutor.execute(action, source)` for each action in the list
- On failure: calls `source.sendSuccess(() -> Component.literal("[Alfred] No pude contactar con el servidor."), false)`

No changes to `AIButler.java`, `ButlerState.java`, `ButlerAction.java`, or `ButlerActionExecutor.java`.

### Argument type

Uses Brigadier's `StringArgumentType.greedyString()` so the full sentence after `ask` is captured as one string (e.g., `/butler ask hola Alfred cómo estás` → `"hola Alfred cómo estás"`).

---

## FastAPI Backend

### Project structure

```
MinecraftButlerAI Backend/
  .env               ← credentials and secret (git-ignored)
  .gitignore
  main.py            ← complete app (single file for Phase 2)
  requirements.txt
```

### `.env`

```
BUTLER_USERNAME=admin
BUTLER_PASSWORD=admin
SECRET_KEY=dev-secret-key-change-in-prod
ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=1440
```

### `requirements.txt`

```
fastapi
uvicorn[standard]
python-jose[cryptography]
python-dotenv
```

### `main.py` responsibilities

- Loads `.env` via `python-dotenv`
- `POST /login`: validates username/password against env vars, returns a signed JWT
- `POST /ask`: protected via `Depends(get_current_user)`, returns a hardcoded mock speak action echoing the input
- `get_current_user`: decodes and validates the JWT bearer token; raises 401 on failure

### Mock response for Phase 2

`/ask` returns:
```json
[{ "type": "speak", "message": "Hola! Recibí: <input message>" }]
```

This function is where the LLM call will be added in Phase 6.

### Startup

```bash
uvicorn main:app --reload --port 8000
```

---

## Error Handling

| Situation | Player sees | Log |
|-----------|-------------|-----|
| FastAPI not running | `[Alfred] No pude contactar con el servidor.` | IOException logged |
| HTTP status != 200 (after retry) | `[Alfred] Error del servidor: <status>` | Response body logged |
| Malformed JSON in response | `[Alfred] Respuesta inesperada del servidor.` | Exception logged |
| Login fails (bad credentials) | `[Alfred] No pude autenticarme con el servidor.` | Status code logged |

All player-facing messages are sent via `server.execute(...)` to guarantee execution on the server thread.

---

## Token Lifecycle

```
/butler ask (first call)
  → no token cached
  → POST /login → store token in ButlerHttpClient.cachedToken
  → POST /ask with token

/butler ask (subsequent calls)
  → token cached → POST /ask directly

/butler ask → 401 response
  → clear cachedToken
  → POST /login → new token
  → POST /ask retry (once)
  → if still 401 → send error message to player
```

---

## Scope Boundaries (Phase 2)

- No LLM calls
- No persistent token storage (token lives only in JVM memory)
- No mod config file for credentials (constants in ButlerHttpClient)
- No TLS/HTTPS (localhost only)
- Only the `speak` action is executed (existing ButlerActionExecutor handles it)
- No multi-action sequencing delays

---

## Testing

**FastAPI (manual):**
```bash
# Start server
uvicorn main:app --reload --port 8000

# Get token
curl -X POST http://localhost:8000/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'

# Call ask with token
curl -X POST http://localhost:8000/ask \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"message":"hola Alfred"}'
```

**Mod (in-game):**
```
.\gradlew.bat runClient
# With FastAPI running:
/butler ask hola Alfred
# Expected: [Alfred] Hola! Recibí: hola Alfred

# With FastAPI NOT running:
/butler ask prueba
# Expected: [Alfred] No pude contactar con el servidor.
```