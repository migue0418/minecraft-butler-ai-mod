# Alfred Mod — FastAPI Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `/butler ask <message>` to the Minecraft mod so it calls the FastAPI backend async, authenticates with JWT, and executes the returned speak actions in-game.

**Architecture:** `ButlerHttpClient` owns all HTTP logic (login, token cache, ask with 401 retry) using Java's built-in `java.net.http.HttpClient` and Gson (bundled with Minecraft). `ButlerCommand` adds the `ask` subcommand: fires the async call immediately, sends "Procesando…" to the player, then re-schedules the result on the server thread via `server.execute()`.

**Tech Stack:** Java 25, Minecraft 26.1.2 (Mojang mappings), Fabric API 0.149.1, Brigadier, Gson (bundled), java.net.http.HttpClient

---

> **Prerequisites:** `MinecraftButlerAI Backend` is running on `http://localhost:8000` for integration testing.
> **Important — Mojang mappings:** All Minecraft class names in this project use Mojang official mappings, NOT Yarn. See the translation table at the bottom.

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `src/main/java/com/miguealguacil/butler/http/ButlerHttpClient.java` | HTTP client: login, token cache, ask, 401 retry |
| Modify | `src/main/java/com/miguealguacil/butler/command/ButlerCommand.java` | Add `ask` subcommand |

No changes to `AIButler.java`, `ButlerState.java`, `ButlerAction.java`, or `ButlerActionExecutor.java`.

---

## Task 1 — Create `ButlerHttpClient`

**Files:**
- Create: `src/main/java/com/miguealguacil/butler/http/ButlerHttpClient.java`

- [ ] **Step 1: Create the file**

```java
package com.miguealguacil.butler.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.miguealguacil.butler.action.ButlerAction;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ButlerHttpClient {

    private static final String BASE_URL = "http://localhost:8000";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "ChangeMe123!";

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();
    private static String cachedToken = null;

    private ButlerHttpClient() {}

    public static final class AuthException extends RuntimeException {
        AuthException() { super("Auth failed"); }
    }

    public static final class ServerException extends RuntimeException {
        ServerException(int status) { super("Server error: " + status); }
    }

    private static CompletableFuture<String> loginAsync() {
        JsonObject body = new JsonObject();
        body.addProperty("username", USERNAME);
        body.addProperty("password", PASSWORD);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() != 200) throw new AuthException();
                    String token = GSON.fromJson(resp.body(), JsonObject.class)
                            .get("access_token").getAsString();
                    cachedToken = token;
                    return token;
                });
    }

    private static CompletableFuture<List<ButlerAction>> askAsync(String message, String token) {
        JsonObject body = new JsonObject();
        body.addProperty("message", message);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/butler/ask"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() == 401) throw new AuthException();
                    if (resp.statusCode() != 200) throw new ServerException(resp.statusCode());
                    return GSON.<List<ButlerAction>>fromJson(
                            resp.body(),
                            new TypeToken<List<ButlerAction>>() {}.getType());
                });
    }

    public static CompletableFuture<List<ButlerAction>> sendAsync(String message) {
        if (cachedToken == null) {
            return loginAsync().thenCompose(token -> askAsync(message, token));
        }
        return askAsync(message, cachedToken)
                .exceptionallyCompose(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof AuthException) {
                        cachedToken = null;
                        return loginAsync().thenCompose(token -> askAsync(message, token));
                    }
                    return CompletableFuture.failedFuture(cause);
                });
    }
}
```

- [ ] **Step 2: Compile**

```powershell
cd "C:\Users\migue\Documents\Proyectos\MinecraftButlerAI"
.\gradlew.bat compileJava
```

Expected: `BUILD SUCCESSFUL`

If Gson import fails (`com.google.gson` not found), it means Gson is not on the compile classpath. Fix by adding to `build.gradle` dependencies:
```groovy
implementation "com.google.code.gson:gson:2.10.1"
```
Then run `.\gradlew.bat compileJava` again.

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/miguealguacil/butler/http/ButlerHttpClient.java
git commit -m "feat: add ButlerHttpClient with JWT login and async ask"
```

---

## Task 2 — Add `/butler ask` to `ButlerCommand`

**Files:**
- Modify: `src/main/java/com/miguealguacil/butler/command/ButlerCommand.java`

The current file registers 5 subcommands inside `dispatcher.register(Commands.literal("butler").then(...))`. Add the `ask` subcommand as a sixth `.then(...)` branch.

- [ ] **Step 1: Replace the full contents of `ButlerCommand.java`**

```java
package com.miguealguacil.butler.command;

import com.miguealguacil.butler.AIButler;
import com.miguealguacil.butler.action.ButlerAction;
import com.miguealguacil.butler.action.ButlerActionExecutor;
import com.miguealguacil.butler.http.ButlerHttpClient;
import com.miguealguacil.butler.state.ButlerState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
                            new ButlerAction("speak", "He recibido una acción mock correctamente."),
                            ctx.getSource());
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

- [ ] **Step 2: Compile**

```powershell
.\gradlew.bat compileJava
```

Expected: `BUILD SUCCESSFUL`

Common failures:
- `StringArgumentType` not found → import is `com.mojang.brigadier.arguments.StringArgumentType` (pure Brigadier, no remapping)
- `MinecraftServer` not found → import is `net.minecraft.server.MinecraftServer`
- `getServer()` not found → check spelling; it is `source.getServer()`

- [ ] **Step 3: Full build**

```powershell
.\gradlew.bat build
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/miguealguacil/butler/command/ButlerCommand.java
git commit -m "feat: add /butler ask command with async FastAPI bridge"
```

---

## Task 3 — Integration test in-game

> Run both FastAPI and the Minecraft client simultaneously.

- [ ] **Step 1: Start the FastAPI backend (separate terminal)**

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

- [ ] **Step 3: Test the happy path**

Open a singleplayer world with cheats. Run:
```
/butler ask hola Alfred
```

Expected (two messages appear):
```
[Alfred] Procesando...
[Alfred] Hola! Recibí: hola Alfred
```

The first message appears immediately. The second appears after the HTTP round-trip (~50ms on localhost).

- [ ] **Step 4: Test error handling — stop FastAPI first**

Stop uvicorn (Ctrl+C in its terminal). Then in Minecraft:
```
/butler ask prueba sin servidor
```

Expected:
```
[Alfred] Procesando...
[Alfred] No pude contactar con el servidor.
```

- [ ] **Step 5: Test token caching — restart FastAPI**

Start uvicorn again. Run `/butler ask` twice in a row quickly:
```
/butler ask primera llamada
/butler ask segunda llamada
```

Expected: both work. The second call skips `/login` (token cached from the first).
You can verify in the uvicorn logs: the first call shows two requests (`POST /api/auth/login`, `POST /api/butler/ask`), the second shows only one (`POST /api/butler/ask`).

- [ ] **Step 6: Final commit**

```powershell
git add -A
git commit -m "chore: verify Phase 2 FastAPI bridge works in-game"
```

---

## Mojang mappings reference (this project only)

| Yarn (tutorials) | Mojang (this project) |
|---|---|
| `net.minecraft.util.math.BlockPos` | `net.minecraft.core.BlockPos` |
| `ServerPlayerEntity` | `net.minecraft.server.level.ServerPlayer` |
| `ServerCommandSource` | `net.minecraft.commands.CommandSourceStack` |
| `Text.literal()` | `Component.literal()` (net.minecraft.network.chat) |
| `CommandManager.literal()` | `Commands.literal()` (net.minecraft.commands) |
| `source.sendFeedback(...)` | `source.sendSuccess(...)` |
| `player.getBlockPos()` | `player.blockPosition()` |
| `source.getPlayer()` | `source.getPlayerOrException()` |

---

## Run command reference

| Command | Purpose |
|---------|---------|
| `.\gradlew.bat compileJava` | Fast compile check |
| `.\gradlew.bat build` | Full build |
| `.\gradlew.bat runClient` | Launch Minecraft |