# 07 — Network Protocol

> 🇮🇹 Versione italiana: [`it/07-protocollo.md`](it/07-protocollo.md)

The network layer between client and server uses **Java object serialization** over TCP (`ObjectInputStream` / `ObjectOutputStream`). All messages are plain Java objects that implement `Serializable` — no text framing, no JSON, no binary hand-rolling.

> **Note:** The legacy text protocol (`STATE` verb, `StateSerializer`) has been removed. The server communicates exclusively via Java object serialization (`SnapshotMessage`, `OkMessage`, `ErrMessage`). All clients render from the latest `SnapshotMessage`.

---

## Transport

```
Client (first)                            Server
  │                                          │
  │  new ObjectOutputStream(socketOut)       │
  │  out.flush()                             │
  │  new ObjectInputStream(socketIn)         │  ← OOS must be flushed before OIS is opened
  │                                          │     to avoid deadlock on the header exchange
  │─── LoginMessage(nick, 0) ───────────────▶│  TcpClientHandler registers TcpVirtualView
  │◀── OkMessage("LOBBY_FIRST") ────────────│  first client: must supply player count
  │─── LoginMessage(nick, n) ───────────────▶│  n ∈ [2, 5]
  │◀── OkMessage("LOBBY_ACCEPTED") ─────────│
  │◀── InfoMessage(availableColors) ────────│  colour selection handshake
  │─── ColorMessage(color or null) ─────────▶│  null = auto-assign first available
  │◀── OkMessage("COLOR_ACCEPTED RED") ─────│
  │                                          │
  │─── StartMessage ────────────────────────▶│  once all players are connected
  │◀─── SnapshotMessage ────────────────────│  broadcast to ALL clients
  │◀─── OkMessage("GAME_STARTED ...") ──────│
  │                                          │
  │─── CommandMessage("PLACE alice B") ─────▶│
  │◀─── SnapshotMessage ────────────────────│  broadcast to ALL clients
  │◀─── OkMessage("PLACE alice -> B") ──────│  only to sender
  │                                          │
  │  [game ends — GAME_OVER phase]           │
  │◀─── RankingMessage ─────────────────────│  broadcast (only if DB configured — FA1)
  │                                          │

Client (subsequent)                       Server
  │─── LoginMessage(nick, 0) ───────────────▶│
  │◀── OkMessage("LOBBY_JOINED n/m") ───────│  e.g. "LOBBY_JOINED 2/3"
  │◀── InfoMessage(availableColors) ────────│
  │─── ColorMessage(color or null) ─────────▶│
  │◀── OkMessage("COLOR_ACCEPTED BLUE") ────│
  │                                          │
```

**Key difference from the old text protocol:** The server no longer sends `EVT` or `OK` lines. Instead, every Model mutation triggers a full `SnapshotMessage` broadcast to all connected clients, plus an `OkMessage` or `ErrMessage` to the command sender.

---

## Client → Server messages

All extend [`ClientMessage`](../src/main/java/mesos/network/client/ClientMessage.java) (abstract, `Serializable`). `ClientMessage` uses the **double-dispatch / visitor** pattern: each subclass calls the appropriate method via `process(Controller controller)`.

| Class | When sent | `process()` behaviour |
|---|---|---|
| `LoginMessage(nick, numPlayers)` | Step 1: always `numPlayers=0`. Step 2: only if `LOBBY_FIRST`, carries `n ∈ [2,5]`. | Handled directly by `TcpClientHandler`, never queued. First message registers the nick; second message sets `expectedPlayers`. |
| `ColorMessage(color)` | After lobby, in response to `InfoMessage`. `color` may be `null` (auto-assign). | Handled directly by `TcpClientHandler`, never queued. Server assigns the requested colour or the first available one. |
| `StartMessage` | Any client, once lobby is full | Builds `Game` + `Controller` from registered nicks, wires `ModelListener`, calls `processCmd("START")`, broadcasts initial `SnapshotMessage`. |
| `CommandMessage(cmd)` | Any game command | Calls `controller.processCmd(cmd)`, wraps reply in `OkMessage` or `ErrMessage`, sends to sender only. |

`LoginMessage` and `ColorMessage` are handled at the transport layer and never enter the `BlockingQueue`.

---

## Server → Client messages

All implement [`ServerMessage`](../src/main/java/mesos/network/ServerMessage.java) (marker interface, `Serializable`).

| Class | When sent | Recipient |
|---|---|---|
| `OkMessage("LOBBY_FIRST")` | First client connects | Sender only |
| `OkMessage("LOBBY_JOINED n/m")` | Subsequent client connects | Sender only |
| `OkMessage("LOBBY_ACCEPTED")` | Server accepts the player count | First client only |
| `InfoMessage(nick, idx, total, colors)` | After lobby step, before game loop | Sender only — carries available totem colours |
| `OkMessage("COLOR_ACCEPTED <color>")` | Colour successfully assigned | Sender only |
| `OkMessage(text)` | Game command succeeded | Sender only |
| `ErrMessage(reason)` | Command failed, invalid login, disconnection | Sender only (or broadcast for `PLAYER_DISCONNECTED`) |
| `SnapshotMessage` | After every Model mutation | All connected clients (broadcast) |
| `RankingMessage(entries)` | After GAME_OVER, only if DB configured (FA1) | All connected clients (broadcast) |
| `HeartbeatMessage` | Every **2 seconds**, from server startup | All connected clients (broadcast) |

`HeartbeatMessage` carries no data. Its only purpose is to keep the TCP stream alive and let clients detect a crashed server. `TcpTransport` skips heartbeats during the synchronous handshake via `readHandshake()`; after that the reader thread delivers them to the `onMessage` callback, which updates a timestamp without printing anything.

---

## `SnapshotMessage` structure

```java
SnapshotMessage {  // serialVersionUID = 4
    String phase;           // GamePhase enum name: SETUP | PLACEMENT | ACTION_RESOLUTION |
                            //   END_OF_ROUND | FINAL_SCORING | GAME_OVER
    int    round;           // 1..10
    String era;             // Era enum name: I | II | III

    String currentPlacementPlayerId;  // nick of the player whose turn it is to place;
                                      // null if phase != PLACEMENT or all players have placed
    String currentExecutionPlayerId;  // nick of the player whose turn it is to execute;
                                      // null if phase != ACTION_RESOLUTION or all done
    String extraPickPendingId;        // non-null while a player awaits their B020 extra top-row pick;
                                      // set after EXECUTE when ExtraTopRowPickEffect is active,
                                      // cleared when EXTRA_PICK or SKIP_EXTRA is received

    List<SlotSnapshot>   topRow;      // cards in the top visible row
    List<SlotSnapshot>   bottomRow;   // cards in the bottom visible row
    List<TileSnapshot>   offerTrack;  // offer tile occupancy
    List<PlayerSnapshot> players;     // per-player state
}

SlotSnapshot {
    String kind;  // EMPTY | CHARACTER | BUILDING | EVENT | FINAL_EVENT
    String name;  // card name, null when EMPTY
}

TileSnapshot {
    String tileId;    // OfferTileId enum name: A | B | C | D | E | F | G
    String occupant;  // player nick or "free"
}

PlayerSnapshot {  // serialVersionUID = 2
    String id;             // player nick
    int    food;
    int    prestigePoints;
    String selectedTile;   // tile id or "-" (no tile selected)
    String totemColor;     // TotemColor enum name: RED | BLUE | WHITE | YELLOW | PURPLE
                           // assigned during colour handshake before game start
    List<TribeCardRef> tribe;  // acquired character + building cards (public info in MESOS);
                               // empty until the player executes and picks cards
}

TribeCardRef {  // serialVersionUID = 1
    String kind;  // CHARACTER | BUILDING
    String name;  // card name (-> card art via CardArtManifest, like SlotSnapshot.name)
    String type;  // CharacterType enum name for characters; null for buildings
}
```

---

## Command verbs (inside `CommandMessage`)

These are the same text verbs used by `Controller.processCmd()`. `CommandMessage` wraps them as plain strings.

| Verb | Args | Notes |
|---|---|---|
| `PLACE` | `<nick> <tileId>` | tileId is `A`..`G`. Fails if occupied. Only valid in PLACEMENT phase. Auto-advances to ACTION_RESOLUTION when all players have placed. |
| `EXECUTE` | `<nick> [ROW:idx ...]` | e.g. `EXECUTE alice TOP:0 BOTTOM:2`. Empty picks valid for tile A. Only valid in ACTION_RESOLUTION. Auto-advances to next PLACEMENT (or end-game) when all players are done. |
| `EXTRA_PICK` | `<nick> <TOP:idx>` | Only if player owns the extra-pick building effect. Only valid in ACTION_RESOLUTION. |
| `SKIP_EXTRA` | `<nick>` | Skips the extra pick and marks the player done. Only valid in ACTION_RESOLUTION. |

Verbs are **case-insensitive**; player nicks are **case-sensitive**; tile and row IDs are case-insensitive.

---

## `OkMessage` / `ErrMessage` text formats

`OkMessage.getText()` mirrors the old text-protocol ACK strings:

```
GAME_STARTED players=alice,bob
PLACE alice -> B
EXECUTE alice picks=2
EXTRA_PICK alice
SKIP_EXTRA alice
```

`ErrMessage.getReason()` examples:

```
GAME_ALREADY_STARTED
NOT_ENOUGH_PLAYERS 1/2
NICKNAME_TAKEN alice
PLAYER_DISCONNECTED bob
GAME_NOT_STARTED
INVALID_PLAYER_COUNT: 7
PLAYER_NOT_FOUND alice
TILE_NOT_FOUND Z
PLACE wrong_phase=ACTION_RESOLUTION   ← sent in wrong phase
PLACE_FAILED B                        ← tile occupied
NO_TILE_SELECTED alice                ← EXECUTE before PLACE
BAD_PICK TOP-0                        ← use ":" not "-"
NO_EXTRA_PICK_EFFECT
EXECUTE <java exception msg>          ← Model threw RuntimeException
```

---

## Multi-client broadcast

Every `CommandMessage` that triggers a Model mutation causes `StartMessage` (or `ModelListener`'s lambda) to call `ServerRegistry.broadcast(SnapshotMessage.from(game))`. All connected clients receive the same snapshot simultaneously, regardless of which client sent the command. There is no per-client filtering.

---

## RMI transport

The RMI transport carries the **same `ClientMessage` / `ServerMessage` objects** through a remote method call instead of a socket stream.

```java
// Server-side interface (RemoteGameService)
String login(String nick, RemoteGameCallback callback) throws RemoteException;
// returns "LOBBY_FIRST" | "LOBBY_JOINED n/m" — throws RemoteException on NICKNAME_TAKEN
void setNumPlayers(int n) throws RemoteException;
// called by first client after LOBBY_FIRST — throws RemoteException if n ∉ [2,5]
void sendMessage(ClientMessage msg, String nick) throws RemoteException;

// Client-side callback (RemoteGameCallback)
void onServerMessage(ServerMessage message) throws RemoteException;
```

`RemoteGameServiceImpl.sendMessage` enqueues the `ClientMessage` into the same `BlockingQueue` as TCP — the `ControllerWorker` processes both transports identically. `RmiVirtualView` wraps the client's `RemoteGameCallback` stub and calls `onServerMessage` to push `SnapshotMessage` / `OkMessage` / `ErrMessage` back.

The RMI **client** side is implemented by `RmiTransport` in `mesos.client`, which extends `UnicastRemoteObject` and implements both `Transport` and `RemoteGameCallback`.

---

## Ordering guarantee

`SnapshotMessage` is broadcast **before** `OkMessage` is sent to the command's sender. This is because the `ModelListener` callback fires synchronously during `controller.processCmd(cmd)` — the broadcast happens inside the call, before it returns. The sender therefore receives the snapshot first, then the ACK.

---

## Serialization notes

- All classes in `mesos.network` declare `serialVersionUID`.
- `VirtualView`, `ServerRegistry` fields in `ClientMessage` are `transient` — they are injected server-side by `TcpClientHandler` / `ControllerWorker` after deserialization.
- Changing field names or types in any `Serializable` class breaks deserialization with any already-connected client. Increment `serialVersionUID` when making incompatible changes.

---

## Limitations

- **Disconnect ends the game for everyone.** When any player disconnects, `ServerRegistry.handleDisconnection` broadcasts `ErrMessage("PLAYER_DISCONNECTED nick")`, nulls the controller, clears the view map, and stops the heartbeat. All remaining clients exit via `System.exit(0)` on receiving the message. There is no hot-reconnect for mid-game drops (FA2 covers crash/restart recovery, but not true mid-game reconnect).
- **No per-client event filtering.** All clients receive the full `SnapshotMessage` after every mutation, including information about other players' hands.
- **No authentication.** Nicknames are trusted as-is; no password or token.
- **No versioning handshake.** Client and server must be compiled from the same source — mismatched `serialVersionUID` values cause `InvalidClassException` at deserialization.
