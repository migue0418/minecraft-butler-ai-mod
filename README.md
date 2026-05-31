# MinecraftButlerAI — Mod

> The in-game half of MinecraftButlerAI. Adds Alfred, an AI butler entity you can summon, command, and talk to —
> in chat or by voice. Alfred executes real actions in the world: managing chests, moving, responding.

![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)
![Fabric](https://img.shields.io/badge/Fabric-Minecraft%2026.1.2-716C6C?logo=curseforge&logoColor=white)
![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-62B47A?logo=minecraft&logoColor=white)

## Demo

> 📹 Demo video coming soon.

## Part of the MinecraftButlerAI system

This mod is the **in-game client**. The AI brain lives in the backend:

```
[You]  →  /butler ask "put diamonds in the diamond chest"
              │
              ▼  HTTP + JWT
[Backend]  →  LangGraph agent  →  RAG · Whisper · Redis memory
              │
              ▼  ButlerAction[]
[Mod]  →  executes: move_to_position / speak / chest distribute / ...
```

Backend repo: [minecraft-butler-ai-backend](https://github.com/migue0418/minecraft-butler-ai-backend)

## Features

**Alfred entity**
- Summon Alfred anywhere with `/butler spawn`
- Make him follow you or stand by
- Custom renderer and model

**In-game commands**
```
/butler ask <message>       Ask Alfred anything — he reasons and acts
/butler spawn               Summon Alfred at your position
/butler follow              Alfred follows you
/butler stop                Alfred stays put
/butler here                Save current position as Alfred's target
/butler pos                 Show your current coordinates
```

**Chest management**
```
/butler chest register <name>           Register the chest you're looking at
/butler chest unregister <name>         Remove a chest from the registry
/butler chest list                      List all registered chests
/butler chest inspect <name>            Show chest contents
/butler chest setaccepts <name> <items> Set which items this chest accepts
/butler chest move <from> <to>          Move all items from one chest to another
/butler chest distribute <src>          Distribute chest contents to matching chests
```

**Voice input** — send audio to the backend and Alfred transcribes and acts on it (via the backend's `/ask-voice` endpoint with faster-whisper)

## Requirements

- Minecraft 26.1.2 (Fabric)
- Fabric Loader 0.19.2
- [minecraft-butler-ai-backend](https://github.com/migue0418/minecraft-butler-ai-backend) running at `http://localhost:8000`

## Setup

1. Start the backend (see [backend setup](https://github.com/migue0418/minecraft-butler-ai-backend#quick-start-docker))
2. Build the mod:
   ```bash
   ./gradlew build
   ```
3. Copy the `.jar` from `build/libs/` into your Minecraft `mods/` folder
4. Launch Minecraft with Fabric — Alfred is ready

## How it works

The mod communicates with the backend over HTTP. On startup it authenticates (JWT), then every `/butler ask` sends the message to `/api/butler/ask` and receives a list of `ButlerAction` objects that are executed server-side:

```java
// ButlerAction record
record ButlerAction(String type, String message, Integer x, Integer y, Integer z)
```

Supported action types: `speak`, `move_to_position`, and any new types added in the backend.

## Project structure

```
src/
├── main/java/com/miguealguacil/butler/
│   ├── action/        # ButlerAction record + executor
│   ├── chest/         # ChestRegistry, ChestItemMover, ItemFilter
│   ├── command/       # /butler command tree
│   ├── entity/        # Alfred entity + AI goals
│   ├── http/          # ButlerHttpClient (async, JWT auto-refresh)
│   └── state/         # ButlerState (position, Alfred reference)
└── client/java/...    # Client-side renderer for Alfred
```
