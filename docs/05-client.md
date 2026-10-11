# 05 — Client-side View

> 🇮🇹 Versione italiana: [`it/05-client.md`](it/05-client.md)

The user-facing half of the View. Two view implementations are available:

- **Text UI (TUI)** — package [`mesos.client.tui`](../src/main/java/mesos/client/tui), for terminal play.
- **Swing GUI** — package [`mesos.client.gui`](../src/main/java/mesos/client/gui) (controller) and four sub-packages of `mesos.client.ui`: [`model`](../src/main/java/mesos/client/ui/model), [`panel`](../src/main/java/mesos/client/ui/panel), [`widget`](../src/main/java/mesos/client/ui/widget), [`resource`](../src/main/java/mesos/client/ui/resource).

All clients communicate with the server using the [`Transport`](../src/main/java/mesos/client/Transport.java)
abstraction, which hides whether the underlying technology is TCP object serialization or RMI.

---

## Package map (`mesos.client`)

```
mesos.client/
├── Transport.java        interface — abstracts TCP vs RMI from the view
├── TcpTransport.java     Transport over TCP (ObjectInputStream / ObjectOutputStream)
├── RmiTransport.java     Transport over RMI (UnicastRemoteObject + RemoteGameCallback)
├── Launcher.java         unified entry point — selects view × transport
├── tui/
│   ├── TuiClient.java    TUI view — works with any Transport
│   ├── BoardRenderer.java ASCII board renderer from SnapshotMessage
│   └── AutoPlayer.java   automated smoke-test client
├── gui/
│   └── GuiClient.java    Swing GUI — main controller (JFrame + CardLayout + message routing)
└── ui/
    ├── model/
    │   ├── GameSnapshot.java    client-side mirror of server state; notifies Swing panels on update
    │   ├── PhaseController.java state machine: derives GuiPhase from GameSnapshot for the local player
    │   ├── GuiPhase.java        enum — 9 UI states (LOBBY_*, PLACEMENT_*, ACTION_*, GAME_OVER)
    │   ├── PicksRemaining.java  record (top, bottom) with isDone()
    │   └── TribeCounts.java     per-player character + building counts for TribeCountsBar
    ├── panel/
    │   ├── BoardPanel.java         main board: RowPanel × 2 + OfferTrackPanel + TurnOrderTilePanel
    │   ├── PlayerPanel.java        per-player sidebar (totem, name, food, prestige, selected tile)
    │   ├── StatusBar.java          top strip — current phase chip, round, era, turn indicator
    │   ├── ActionPanel.java        bottom strip — phase-specific instructions + action button
    │   ├── LobbyPanel.java         totem colour selector (5 coloured cards, confirm + start buttons)
    │   ├── EndGamePanel.java       end-screen — MESOS logo + ranking grid
    │   ├── DisconnectPanel.java    error screen (OTHER_DISCONNECTED / LOST_CONNECTION / SERVER_UNREACHABLE)
    │   ├── CountPanel.java         4 big buttons (2/3/4/5 players) — shown to first player only
    │   └── TribeCardsPanel.java    expandable tribe view (CHARACTER + BUILDING card thumbnails)
    ├── resource/
    │   ├── Theme.java           centralised colour palette and fonts
    │   ├── Resources.java       PNG loader with cache (totems, tiles, card fronts, box art)
    │   ├── CardArtManifest.java card name → PDF page mapping for card images
    │   └── PlayerColors.java    player position → totem colour string
    └── widget/
        ├── CardComponent.java      single card slot (EMPTY / BACK / CHARACTER / BUILDING / EVENT)
        ├── OfferTileComponent.java offer-track tile with totem standee
        ├── OfferTrackPanel.java    complete offer track (7 tiles)
        ├── TurnOrderTilePanel.java cave image with per-player-count turn-order overlay
        ├── RowPanel.java           horizontal strip of CardComponent
        ├── TotemBadge.java         totem PNG icon widget with cache
        ├── ResourceCounter.java    resource token counter (food / prestige points)
        ├── ActionPicksBar.java     "PICKS REMAINING" counter during ACTION_MINE
        ├── ExtraPickBanner.java    yellow banner for extra-pick bonus (B020)
        ├── TribeCountsBar.java     compact grid of character + building type token badges
        ├── TypeTokenBadge.java     single character-type token with count badge
        ├── BuildingBadge.java      building token icon with count badge
        └── CardZoomOverlay.java    enlarged card preview on hover
```

---

## `Transport` interface

```java
public interface Transport {
    void connect(String host, int port, String nick,
                 Supplier<Integer> askNumPlayers,
                 Function<List<TotemColor>, TotemColor> askColor) throws Exception;
    void send(ClientMessage msg) throws Exception;
    void setOnMessage(Consumer<ServerMessage> listener);
    void close();
}
```

The view (TUI or GUI) only interacts with this interface. It has no knowledge of sockets, streams, or RMI stubs.

- `askNumPlayers` — a `Supplier<Integer>` invoked **only** when the server responds `LOBBY_FIRST` (first connecting client). For all other clients it is never called.
- `askColor` — a `Function<List<TotemColor>, TotemColor>` invoked after every lobby handshake (all clients). Receives the list of available totem colours and returns the player's choice.

---

## `TcpTransport`

Opens a TCP socket to the server. Handles `ObjectOutputStream` / `ObjectInputStream`
(OOS must be created and flushed before OIS — anti-deadlock invariant).

`connect()` performs the full lobby + colour handshake **synchronously** before starting the reader thread. Because the server sends `HeartbeatMessage` every 2 seconds starting at startup, `readHandshake()` skips any heartbeats that arrive between expected handshake messages:

```java
private Object readHandshake(ObjectInputStream in) throws ... {
    Object obj;
    do { obj = in.readObject(); } while (obj instanceof HeartbeatMessage);
    return obj;
}
```

Steps:
1. Sends `LoginMessage(nick, 0)`.
2. Calls `readHandshake()` — reads `OkMessage("LOBBY_FIRST")`, `OkMessage("LOBBY_JOINED n/m")`, or `ErrMessage` (throws on error).
3. If `LOBBY_FIRST`: invokes `askNumPlayers.get()`, sends `LoginMessage(nick, n)`, calls `readHandshake()` for `OkMessage("LOBBY_ACCEPTED")`.
4. Calls `readHandshake()` for `InfoMessage` (available colours) — delivers it to `onMessage` callback.
5. Invokes `askColor.apply(availableColors)`, sends `ColorMessage(chosen)`.
6. Calls `readHandshake()` for `OkMessage("COLOR_ACCEPTED <color>")` — delivers it to `onMessage`.
7. Starts the `tcp-reader` daemon thread for subsequent game messages.

Key detail: `send()` synchronizes on `out` because the stdin thread and any other callers must not interleave writes on the same stream.

---

## `RmiTransport`

Extends `UnicastRemoteObject` and implements both `Transport` and `RemoteGameCallback`.
The `super()` call in the constructor exports the object so the server can call
`onServerMessage()` as a remote method. Without the export, the server receives a
`RemoteException` on the first push.

On `connect()`: looks up `RemoteGameService` in the RMI registry, calls `service.login(nick, this)` — which returns `"LOBBY_FIRST"` or `"LOBBY_JOINED n/m"`. If `LOBBY_FIRST`, invokes `askNumPlayers.get()` and calls `service.setNumPlayers(n)`. All `RemoteException`s are caught, printed, and re-thrown as plain `Exception`.

On push from server: the JVM calls `onServerMessage(message)` on an RMI-managed thread;
the method forwards the message to the registered listener (e.g. `TuiClient`'s render
callback).

---

## `Launcher` — unified entry point

```
java mesos.client.Launcher [host] [port] [clientIp]
```

At startup, `Launcher` prompts interactively for interface (TUI / GUI) and transport (TCP / RMI). CLI flags are not implemented — all selection is interactive.

Port defaults are transport-specific: TCP → 1234, RMI → 1099. The optional `clientIp` (args[2]) overrides the `java.rmi.server.hostname` property for RMI clients; if omitted, the correct outbound IP is auto-detected via a socket probe to the server.

```
Select interface:
  1) TUI  (text)
  2) GUI  (Swing)
> 1
Select transport:
  1) TCP  (socket)
  2) RMI
> 2
[connected] logged in as 'alice'
```

---

## TUI classes (`mesos.client.tui`)

| Class | What it does |
|---|---|
| [`TuiClient`](../src/main/java/mesos/client/tui/TuiClient.java) | Terminal view. Receives a `Transport` from `Launcher` (or creates `TcpTransport` when called directly via `main`). Registers an `onMessage` callback that renders inbound `SnapshotMessage`/`OkMessage`/`ErrMessage`. Ignores `HeartbeatMessage` but updates a timestamp used by the watchdog. Starts a watchdog daemon thread that exits the process if no message arrives for 6 s (server crash detection). Main thread reads stdin and calls `transport.send()`. |
| [`BoardRenderer`](../src/main/java/mesos/client/tui/BoardRenderer.java) | Renders a `SnapshotMessage` as an ASCII board to stdout. Called by the `onMessage` callback whenever a new snapshot arrives. |
| [`AutoPlayer`](../src/main/java/mesos/client/tui/AutoPlayer.java) | Automated client that plays a full game without human input. Connects with an auto-generated nick (`auto_XXXX`), waits 5 s for a second client, starts the game, then drives **all** players through every round by reading each snapshot and computing PLACE / EXECUTE picks. Useful for end-to-end smoke testing. |

---

## Threading model

```
[Main thread: stdin loop]    [Transport reader thread]     [Watchdog thread]
        │                             │                            │
        │  stdin.nextLine() (blocks)  │  ServerMessage arrives     │  sleep(2 s) loop
        ▼                             ▼                            ▼
  "place B"               lastMsg[0] = now()             if (now - lastMsg[0] > 6 s):
        │                 if HeartbeatMessage: return        System.exit(0)
        │                 synchronized (OUT_LOCK) {
        ▼                   BoardRenderer.render(snap)
  transport.send(            // or print [OK] / [ERR]
    CommandMessage(...))   }
```

Three threads run concurrently after `connect()`:
- **Main thread** — reads stdin, sends `ClientMessage` objects.
- **Reader thread** (`tcp-reader`) — delivers `ServerMessage` to the `onMessage` callback.
- **Watchdog** — daemon thread that polls `lastMsg[0]` every 2 s; exits with `System.exit(0)` if no message has arrived for **6 seconds** (server crash / network drop).

`HeartbeatMessage` is the only `ServerMessage` that the callback consumes silently (just updates `lastMsg[0]`) without printing anything.

All three threads synchronize console output on `OUT_LOCK`. For RMI, messages arrive on a JVM-managed RMI thread — same `OUT_LOCK` applies.

---

## Connection lifecycle (`TuiClient.start`)

```
1. transport.setOnMessage(msg -> { render / print })
2. transport.connect(host, port, nick, () -> { /* ask user for n ∈ [2,5] */ })
       TCP:  Socket → OOS.flush() → OIS → LoginMessage(nick,0)
             ← LOBBY_FIRST → invoke Supplier → LoginMessage(nick,n) → ← LOBBY_ACCEPTED
             ← LOBBY_JOINED n/m → (no action)
             ← InfoMessage(availableColors) → ColorMessage(chosen) → ← COLOR_ACCEPTED
             → start tcp-reader thread
       RMI:  registry.lookup("mesos-game") → service.login(nick, this)
             if LOBBY_FIRST → invoke Supplier → service.setNumPlayers(n)
3. stdin command loop (until "quit" or EOF)
4. transport.close()
       TCP:  socket.close()
       RMI:  UnicastRemoteObject.unexportObject(this, true)
```

The player count is negotiated automatically — the first client is prompted by the
transport, all subsequent clients enter without any input. After all expected players
have connected, type `start` to send `StartMessage`.

---

## Commands understood by TuiClient

The client automatically injects the player's own nick — the user never types a nick manually.

| TUI input | CommandMessage sent |
|---|---|
| `start` | `StartMessage` |
| `place <tile>` | `CommandMessage("PLACE <nick> <tile>")` |
| `exec [TOP:n ...]` | `CommandMessage("EXECUTE <nick> TOP:0 …")` |
| `extrapick <TOP:n>` | `CommandMessage("EXTRA_PICK <nick> TOP:2")` |
| `skipextra` | `CommandMessage("SKIP_EXTRA <nick>")` |
| `state` / `board` | re-renders the last `SnapshotMessage` locally (no network call) |
| `help` | prints available commands |
| `quit` | closes transport and exits |

---

## Turn indicators in the TUI

`TuiClient` uses `currentPlacementPlayerId` and `currentExecutionPlayerId` from each `SnapshotMessage` to show whose turn it is:

- **PLACEMENT** — if the field matches your nick: `[YOUR TURN to place — free tiles: ...]`; otherwise: `[Waiting for <nick> to place their totem...]`
- **ACTION_RESOLUTION** — if the field matches your nick: `[YOUR TURN — tile: ...]`; otherwise: `[Waiting for <nick> to execute...]`

---

## `SnapshotMessage` — the state source of truth

Every command that mutates the Model causes the server to broadcast a full `SnapshotMessage`
to all connected clients regardless of transport. The client stores the latest snapshot in
`lastSnapshot` and re-renders it via `BoardRenderer.render(snap)`.

There is no partial-update protocol. The `state` / `board` TUI commands simply re-render
the cached snapshot without contacting the server.

---

## `AutoPlayer` — automated smoke test

`AutoPlayer` connects with an auto-generated nick (`auto_XXXX` based on timestamp), waits 5 s for all expected players to join, sends `StartMessage`, then drives a complete game loop:

```
for each round (1..10):
  1. Wait for PLACEMENT phase snapshot (waitPhase, 6 s timeout)
  2. PLACE each player on a free tile (in snapshot order)
  3. Wait for ACTION_RESOLUTION phase snapshot (auto-advance by Model)
  4. For each player: EXECUTE picks + SKIP_EXTRA (marks player done)
  5. Wait 800 ms for Model to auto-advance to next PLACEMENT (or FINAL_SCORING / GAME_OVER)
  6. If phase == GAME_OVER or FINAL_SCORING: break
```

Phase transitions (PLACEMENT → ACTION_RESOLUTION, ACTION_RESOLUTION → next PLACEMENT) are
driven automatically by the Model — no explicit BEGIN_ACT or END_ROUND commands are needed.

If DB is configured, `AutoPlayer` waits up to 4 s for `RankingMessage` and prints the
aggregated leaderboard. Otherwise it prints scores from the final `SnapshotMessage`.

CLI args: `[host] [port] [nick] [numPlayers] [startDelayMs]` — all optional.

---

## Swing GUI client (`mesos.client.gui` + `mesos.client.ui.{model,panel,widget,resource}`)

Implemented in 2026-06-07. `Launcher` passes the chosen `Transport` to `GuiClient` exactly as it does for `TuiClient`.

### `GuiClient` — the controller

Creates a `JFrame` with a `CardLayout` and manages five named cards:

| Card key | Panel | When shown |
|---|---|---|
| `"LOBBY"` | `LobbyPanel` | Totem colour selection |
| `"COUNT"` | `CountPanel` | Player-count picker — first client only |
| `"GAME"` | composite panel | In-game board + sidebars |
| `"END"` | `EndGamePanel` | After `RankingMessage` |
| `"DISCONNECT"` | `DisconnectPanel` | After `PLAYER_DISCONNECTED` or watchdog timeout |

Incoming `ServerMessage` objects are always dispatched via `SwingUtilities.invokeLater` — the network reader thread never touches Swing components directly.

`switchCardForPhase` forces `countPanel.setVisible(false)` on every non-COUNT card switch and calls `rootCards.repaint()` to prevent ghost buttons from the `CountPanel` lingering over other screens during incremental Swing repaints.

### Display, fullscreen & responsive layout (HiDPI)

`launch()` builds the window **borderless** (`setUndecorated(true)`) and sizes it to the **whole screen** — true fullscreen that also covers the taskbar — via `Toolkit.getScreenSize()` (taken as the max with the default `GraphicsConfiguration` bounds, which on per-monitor-DPI-aware JVMs can report only the work area). This fixes high-DPI panels (e.g. a Surface at 200% scaling, ~`1440×960` logical) where the old fixed `1400×880` window could not go fullscreen and rendered with the wrong proportions. **F11** toggles fullscreen ↔ a normal maximized window; **Esc** leaves fullscreen. (Changing the decoration disposes and re-shows the frame; the CardLayout content and the network state persist across the recreate.)

Everything stays proportional through a single factor in [`Theme`](../src/main/java/mesos/client/ui/resource/Theme.java):

- `Theme.SCALE` — computed once at class-load from the screen relative to the `1400×880` design baseline, `min(w/1400, h/880)`, clamped to `[0.65, 1.6]`; falls back to `1.0` in headless JVMs.
- `Theme.scale(int px)` / `Theme.font(family, style, size)` — applied across the panels/widgets so every `Dimension`, font size, `EmptyBorder` inset and layout `hgap`/`vgap` tracks the display.

Beyond the static factor, the board is **responsive**:

- `RowPanel` lays the card slots out with a custom `CardRowLayout` that makes the cards as large as possible (printed 5:7 aspect, capped) to fill the row and grow with the window, instead of sitting at a fixed size in the top-left corner.
- `BoardPanel` gives the offer track the largest GridBag weight (prominent central band) and uses a `BorderLayout` for the mid zone so the **turn-order tile keeps its width** (`WEST`) while the offer track takes the rest (`CENTER`). (With GridBag the offer track's large preferred width squeezed the weightx=0 turn-order tile to ~1px, hiding it.)
- `ResourceCounter` draws a dark circular "slot" behind each token so the light gold prestige laurel stands out from the cream panel instead of blending in.

### `GameSnapshot` — client-side model

Holds a local copy of the server state, updated on every `SnapshotMessage`. Swing panels register as listeners and call `repaint()` when notified:

```java
snapshot.addListener(() -> SwingUtilities.invokeLater(this::repaint));
```

Fields: `players` (nick → `PlayerState`), `topRow`/`bottomRow` (list of `CardInfo`), `tiles` (tileId → occupant), `phase`, `round`, `era`.

### `PhaseController` — local state machine

Derives the nine `GuiPhase` states from `GameSnapshot` for the **local player**, distinguishing e.g. "my placement turn" from "waiting for others". Also tracks the picks budget (top/bottom slots) for the selected tile, and available colours during lobby.

### `GuiPhase` — nine UI states

```
LOBBY_PICKING_COLOR      LOBBY_WAITING_OTHERS      READY_TO_START
PLACEMENT_MINE           PLACEMENT_WAITING
ACTION_MINE              ACTION_EXTRA_PICK_MINE     ACTION_DONE_WAITING
GAME_OVER
```

`ActionPanel` and `StatusBar` drive all their visual changes (title, hint, button state, gradient colour) from the current `GuiPhase`.

### EDT invariant

**Rule:** every Swing mutation goes through `SwingUtilities.invokeLater`. The network reader thread updates `GameSnapshot` and notifies listeners, which schedule their `repaint()` on the EDT. No panel is ever touched directly from a non-EDT thread.

### GUI threading model

```
[Network reader thread]            [EDT — Swing]              [Watchdog daemon]
        │                               │                            │
  reads ServerMessage                   │                     sleep(2 s) loop
        │                               │                            │
  invokeLater(λ)  ─────────────────►  λ runs:                if now−lastMsg > 6 s:
                                      GameSnapshot.update()      System.exit(0)
                                      → notifies listeners
                                      → panels repaint()
  send() called from                  button click →
  EDT action listeners                transport.send(msg)
```

### `BoardPanel` — cave-art background

`paintComponent` draws the cave-painting as a **full-bleed low-alpha wash** (alpha 0.13) scaled with `Math.max(scaleX, scaleY)` to always cover the entire panel. A previous implementation used a bottom band (alpha 0.42) which produced a "board cut in half" visual artefact once the offer track stopped covering it.

### `LobbyPanel` — `setOpaque(true)`

Declared opaque so Swing knows the gradient fill covers the whole panel bounds. Without this, partial repaints leave `CountPanel` buttons (labelled "2"/"5") visible in the gaps between totem cards.

### Player tribe view — counts (CORE) + expandable cards (removable module)

Each `PlayerPanel` shows the acquired tribe (`PlayerState.tribe`, from the new `PlayerSnapshot.tribe`; public info in MESOS). Two layers:

- **CORE — always visible:** `TribeCountsBar` (`mesos.client.ui.widget`) is a compact grid of six `TypeTokenBadge` (one per `CharacterType`) plus one `BuildingBadge`, each showing a count (faded at 0). Counts come from `TribeCounts` (`mesos.client.ui.model`), a pure Swing-free aggregator. Type icons are BGA-style tokens cropped from the card art's top-right badge via `Resources.typeIcon(CharacterType)` (region `x[0.705,0.985] y[0.015,0.14]`, copied to a standalone image and cached in an `EnumMap`); the building token is drawn vector (a hut). The count badge sits in the token's top-right corner.
- **Removable module — the expansion:** gated by `PlayerPanel.TRIBE_EXPAND_ENABLED`. A chevron toggle expands the panel to show `TribeCardsPanel` (`mesos.client.ui.panel`), the player's real cards as `CardComponent` thumbnails grouped into "Personaggi" / "Edifici". Only one panel is expanded at a time — the coordination lives inside `PlayerPanel` (it collapses sibling panels in the parent column), so `GuiClient` has no dependency on the expansion API. To keep only the counts, set the flag `false`; deleting `TribeCardsPanel` + the guarded block needs no `GuiClient` change. `playersStack` uses `BoxLayout(Y_AXIS)` so an expanded panel grows while the others stay compact.

---

## Running

```bash
# Launcher — interactive selection (recommended)
java -cp target/classes mesos.client.Launcher
java -cp target/classes mesos.client.Launcher 192.168.1.5
java -cp target/classes mesos.client.Launcher 192.168.1.5 9000

# TuiClient directly — TCP only, legacy entry point
java -cp target/classes mesos.client.tui.TuiClient
java -cp target/classes mesos.client.tui.TuiClient 192.168.1.5 9000

# AutoPlayer (automated, headless)
java -cp target/classes mesos.client.tui.AutoPlayer
```

---

## File reference

- [`Transport.java`](../src/main/java/mesos/client/Transport.java) — transport interface
- [`TcpTransport.java`](../src/main/java/mesos/client/TcpTransport.java) — TCP implementation
- [`RmiTransport.java`](../src/main/java/mesos/client/RmiTransport.java) — RMI implementation
- [`Launcher.java`](../src/main/java/mesos/client/Launcher.java) — unified entry point
- [`TuiClient.java`](../src/main/java/mesos/client/tui/TuiClient.java) — TUI view
- [`BoardRenderer.java`](../src/main/java/mesos/client/tui/BoardRenderer.java) — ASCII board renderer
- [`AutoPlayer.java`](../src/main/java/mesos/client/tui/AutoPlayer.java) — automated smoke-test client
- [`GuiClient.java`](../src/main/java/mesos/client/gui/GuiClient.java) — Swing GUI main controller
- [`GameSnapshot.java`](../src/main/java/mesos/client/ui/model/GameSnapshot.java) — client-side model (`ui.model`)
- [`PhaseController.java`](../src/main/java/mesos/client/ui/model/PhaseController.java) — local state machine (`ui.model`)
- [`GuiPhase.java`](../src/main/java/mesos/client/ui/model/GuiPhase.java) — nine UI states enum (`ui.model`)
- [`BoardPanel.java`](../src/main/java/mesos/client/ui/panel/BoardPanel.java) — main board panel (`ui.panel`)
- [`ActionPanel.java`](../src/main/java/mesos/client/ui/panel/ActionPanel.java) — interactive bottom strip (`ui.panel`)
- [`LobbyPanel.java`](../src/main/java/mesos/client/ui/panel/LobbyPanel.java) — lobby colour picker (`ui.panel`)
- [`Theme.java`](../src/main/java/mesos/client/ui/resource/Theme.java) — visual palette constants (`ui.resource`)
- [`CardComponent.java`](../src/main/java/mesos/client/ui/widget/CardComponent.java) — single card slot (`ui.widget`)
- [`OfferTrackPanel.java`](../src/main/java/mesos/client/ui/widget/OfferTrackPanel.java) — offer track (`ui.widget`)