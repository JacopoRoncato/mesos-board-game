# MESOS — Digital Implementation

Java implementation of the board game **MESOS** (Yaniv Kahana & Simone Luciani, Cranio Creations), built for the *Ingegneria del Software* course at Politecnico di Milano (A.A. 2025/2026).

Project degree: **30**
Group: **Jacopo Roncato, Amjed Taha, Marco Ruggieri**

---

## Architecture

Networked MVC following Gian Enrico Conti's *Towards Remote View* pattern:

- **Model + Controller** live on the server (pure game logic, no I/O awareness)
- **View** is split: `VirtualView` per-client outbound channel on the server; TUI or Swing GUI on the client
- **Transport**: Java object serialization over TCP sockets or Java RMI (switchable at runtime)
- **Concurrency**: one `TcpClientHandler` thread per client; all game commands serialized through a single `ControllerWorker` queue — no explicit locking on the Model needed

```
   CLIENT (TUI or Swing GUI)                   SERVER
   ┌───────────────────────┐                   ┌───────────────────────────────────┐
   │  Launcher             │                   │  ServerMain                       │
   │  Transport (TCP/RMI)  │ ── ClientMessage ▶│  TcpClientHandler  ─▶  queue      │
   │  TuiClient / GuiClient│ ◀─ ServerMessage ─│  ControllerWorker  ─▶  Controller │
   └───────────────────────┘                   │  ModelListener     ─▶  broadcast  │
                                               │  ServerRegistry  (VirtualView[])  │
                                               │  Game (Model)                     │
                                               └───────────────────────────────────┘
```

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 17+ (validated on JDK 25.0.2) |
| Build | Maven 3.x (`pom.xml` at project root) |
| Network | `java.net` TCP sockets + Java RMI |
| Serialization | `java.io.ObjectOutputStream` / `ObjectInputStream` |
| GUI | `javax.swing` |
| Observer | `java.beans.PropertyChangeListener` |
| DB (FA1, optional) | MySQL via JDBC (`com.mysql.cj.jdbc.Driver`) |
| Tests | JUnit 5 |

No external dependencies beyond the JDK and the optional MySQL connector.

---

## Implemented features

| Req. | Feature | Status |
|---|---|---|
| 1.1 | TUI client — TCP, multi-player | ✅ |
| 1.2 | Swing GUI client — TCP, multi-player | ✅ |
| 1.3b | RMI transport (TUI) | ✅ |
| 1.3c | RMI transport (GUI) | ✅ |
| 1.4 | JUnit tests — model + controller ≥ 90 % line coverage | ✅ |
| 1.5 | Formal game termination on client disconnect | ✅ |
| 1.6 | Unified `Launcher` (TUI/GUI × TCP/RMI selector) | ✅ |
| 1.7 | Javadoc in English on all public classes / methods | ✅ |
| 1.8 | All source comments in English | ✅ |
| FA1 | Leaderboard persisted to MySQL, broadcast at game-over | ✅ |
| FA2 | Game-state persistence across server restarts (`mesos-save.dat`) | ✅ |

---

## Quick start

### 1 — Build

```bash
mvn -DskipTests package
```

Produces `Derivables/mesos.jar` (fat JAR, no extra dependencies needed).

### 2 — Run the server

```bash
# Default ports (TCP 1234, RMI 1099)
java -cp Derivables/mesos.jar mesos.server.ServerMain

# Custom ports
java -cp Derivables/mesos.jar mesos.server.ServerMain <tcpPort> <rmiPort>

# With MySQL leaderboard (FA1)
java -cp Derivables/mesos.jar mesos.server.ServerMain \
    <tcpPort> <rmiPort> <mysqlHost> <dbUser> <dbPass>
```

The server starts both TCP and RMI listeners simultaneously. Game state is automatically
saved to `mesos-save.dat` in the working directory and reloaded on the next startup (FA2).

### 3 — Run the client (unified launcher)

```bash
# Connect to localhost with default port
java -jar Derivables/mesos.jar

# Connect to a remote server (port defaults to 1234 TCP / 1099 RMI)
java -jar Derivables/mesos.jar <host>

# Connect on a specific port
java -jar Derivables/mesos.jar <host> <port>

# RMI with explicit client IP override (only needed if auto-detection fails)
java -jar Derivables/mesos.jar <host> <rmiPort> <clientIp>
```

The launcher prompts interactively:
1. Interface: **TUI** (text) or **GUI** (Swing)
2. Transport: **TCP** or **RMI**

Then the client asks for **Nickname**, **player count** (2–5, first client only), and **totem colour** (all clients pick from the remaining available colours).

### 4 — Smoke test (2 players, TUI only)

Terminal 1 — server:
```bash
java -cp Derivables/mesos.jar mesos.server.ServerMain
```

Terminal 2 — first client (sets player count):
```bash
java -jar Derivables/mesos.jar
# > 1 (TUI)  > 1 (TCP)
# Nickname: alice
# Number of players (2-5): 2
# Choose color (1-5): 1
```

Terminal 3 — second client:
```bash
java -jar Derivables/mesos.jar
# > 1 (TUI)  > 1 (TCP)
# Nickname: bob
# Choose color (1-4): 1   ← one colour already taken
```

In terminal 2, type `start`. Both clients receive the initial board snapshot.

### 5 — Automated smoke test (AutoPlayer)

```bash
# Start the server, then run two AutoPlayer instances:
java -cp Derivables/mesos.jar mesos.client.tui.AutoPlayer
java -cp Derivables/mesos.jar mesos.client.tui.AutoPlayer
```

`AutoPlayer` drives a complete game for all players automatically and prints the final rankings.

---

## TUI commands

Commands are entered after the game starts. The client automatically prepends your nickname.

| Command | Description |
|---|---|
| `place <tileId>` | Place your totem on the given offer tile (e.g. `place B`) |
| `exec <row>:<slot>` | Execute the card at position row:slot (e.g. `exec TOP:0`) |
| `extrapick <row>:<slot>` | Pick an extra card if entitled (e.g. `extrapick TOP:2`) |
| `skipextra` | Decline the extra pick |
| `start` | Start the game (any player, lobby must be full) |

Tile IDs: `A` `B` `C` `D` `E` `F` `G` (availability depends on player count).
Rows: `TOP` `BOTTOM`.

---

## Project structure

```
src/main/java/mesos/
├── board/         Board state — visible rows, slots, refill, deck factories
├── cards/         Card hierarchy
│   ├── characters/  6 types: Hunter, Shaman, Artist, Builder, Inventor, Gatherer
│   └── events/      4 types: Sustenance, Hunting, ShamanicRitual, CavePaintings
├── client/        Transport abstraction + unified Launcher
│   ├── tui/       TuiClient (interactive), BoardRenderer (ASCII), AutoPlayer (smoke test)
│   ├── gui/       GuiClient (Swing entry point)
│   └── ui/        Swing panels, widgets, models, resources
├── controller/    Controller — command parser + dispatcher
├── db/            ScoreResult, ScoreRepository — MySQL leaderboard (FA1)
├── effects/       BuildingEffect strategy — 8 concrete effects
├── enums/         Type-safe enums (Era, GamePhase, PlayerCount, TotemColor, …)
├── game/          Game (orchestrator), ScoreCalculator
├── network/       Serializable message DTOs
│   ├── client/    LoginMessage, ColorMessage, StartMessage, CommandMessage
│   └── server/    OkMessage, ErrMessage, InfoMessage, SnapshotMessage,
│                  HeartbeatMessage, RankingMessage
├── offer/         OfferTrack, OfferTile, OfferChoice, OfferChoiceValidator
├── order/         TurnOrderTile, TurnOrderSpace
├── player/        Player, Totem
├── server/        ServerMain, ServerRegistry, TcpClientHandler, ControllerWorker,
│                  ModelListener, CallBack, VirtualView, TcpVirtualView,
│                  RmiVirtualView, RemoteGameService, RemoteGameServiceImpl,
│                  GamePersistence (FA2)
└── tribe/         Tribe, CharacterStats, BuildingEffects
```

---

## Testing

```bash
# Run the full test suite
mvn test

# Run with coverage report (JaCoCo)
mvn verify
```

Tests live in `src/test/java/mesos/`. Coverage target: ≥ 90 % line coverage on
`mesos.game`, `mesos.board`, `mesos.cards`, `mesos.effects`, `mesos.offer`,
`mesos.order`, `mesos.player`, `mesos.tribe`, and `mesos.controller`.

Server, client, and network packages are not unit-tested (require integration tests);
`AutoPlayer` provides end-to-end coverage for the full game flow.

---

## Documentation

Per-layer documentation lives in [`docs/`](docs/):

1. [Architecture](docs/01-architecture.md) — MVC design, flow diagrams
2. [Model](docs/02-model.md) — game state, rules-to-code mapping
3. [Controller](docs/03-controller.md) — command parsing, dispatch
4. [Server-side view](docs/04-server.md) — VirtualView, ModelListener, CallBack
5. [Client-side view](docs/05-client.md) — TUI, GUI, AutoPlayer, threading
6. [Listener mechanism](docs/06-listeners.md) — PropertyChangeListener wiring
7. [Wire protocol](docs/07-protocol.md) — object serialization protocol, message hierarchy
8. [Build & run](docs/08-build-run.md) — toolchain, compile, full smoke-test

UML diagrams (PlantUML sources): [`Derivables/uml/`](Derivables/uml/)

