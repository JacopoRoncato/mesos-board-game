# 03 — Controller

> 🇮🇹 Versione italiana: [`it/03-controller.md`](it/03-controller.md)

Single class: [`mesos.controller.Controller`](../src/main/java/mesos/controller/Controller.java). Mirrors Cugola's *Server 2'* variante (slides 43–46). Stateless command dispatcher — holds only a reference to `Game`.

## Role

The Controller sits **between the VirtualView and the Model**. Its job is narrow:

1. Receive a textual command from the VirtualView (one line at a time).
2. Parse it into a verb + arguments.
3. Validate that the requested action is legal in the current Model state.
4. Dispatch to the appropriate Model method.
5. Return a one-line reply: `OK ...` on success, `ERR <verb> <reason>` on failure.

The Controller does **not**:
- Touch the network — that's the VirtualView's job
- Touch the UI — that's the client's job
- Hold state — all state lives in the Model
- Push events back to the View — that goes through `PropertyChangeListener` directly

## Class shape

```java
public class Controller {
    private final Game game;

    public Controller(Game game) {
        this.game = game;
    }

    public void setListener(PropertyChangeListener listener) {
        game.setListener(listener);
    }

    public String processCmd(String raw) {
        // parse + dispatch + return reply
    }
}
```

Three public methods. The Model is held by reference (the Controller does not own its lifecycle — the bootstrap constructs the Game first, then the Controller).

## `setListener` — the only Model-to-View bridge

`StartMessage.process()` constructs a `ModelListener` (wrapping a `CallBack` lambda) and passes it here. The Controller forwards it down to `Game.setListener`, which in turn propagates to every `Player`. After this single call, any state change in the Model fires a `PropertyChangeEvent` that the `ModelListener`'s lambda turns into a `ServerRegistry.broadcast(SnapshotMessage.from(game))` call.

The Controller doesn't keep a reference to the listener — it's pure forwarding. See [06 — Listeners](06-listeners.md) for the full propagation chain.

## `processCmd` — the only command entry

`processCmd(String)` is the only way to drive the Model from the outside. It is called in two contexts:

1. **`StartMessage.process(controller)`** — calls `processCmd("START")` directly once, before the listener is wired, to initialize the game state without broadcasting.
2. **`CommandMessage.process(controller)`** — called by the `ControllerWorker` thread for every subsequent command (PLACE, EXECUTE, EXTRA_PICK, SKIP_EXTRA, etc.). The `ControllerWorker` is the single thread that drains the `BlockingQueue<ClientMessage>`, so `processCmd` is always called from one thread.

The RMI transport also routes through the same `BlockingQueue` via `RemoteGameServiceImpl.sendMessage` — there is no separate entry point for RMI.

Inside, it splits on whitespace, uppercases the verb, and `switch`es:

| Verb | Args | Model call | Reply (success) |
|---|---|---|---|
| `START` | — | `game.startGame()` | `OK START phase=PLACEMENT` |
| `PLACE` | `<playerId> <tileId>` | `game.placeTotem(player, tile)` — auto-advances to ACTION_RESOLUTION when all players have placed | `OK PLACE P1 -> B` |
| `EXECUTE` | `<playerId> [row:slot …]` | `tile.executeChoice(choice, player, board)` then: if no B020 effect → `game.markPlayerDone(player)` (auto-advances to next PLACEMENT when all done); if B020 effect present → `game.setExtraPickPending(player)` (signals clients to show extra-pick UI) | `OK EXECUTE P1 picks=2` |
| `EXTRA_PICK` | `<playerId> <row:slot>` | `effect.executeTopRowPick(...)` then `game.markPlayerDone(player)` | `OK EXTRA_PICK P1` |
| `SKIP_EXTRA` | `<playerId>` | `game.markPlayerDone(player)` | `OK SKIP_EXTRA P1` |

Unknown verbs return `ERR UNKNOWN_CMD <verb>`. Any `RuntimeException` thrown by the Model (e.g. `IllegalStateException` from `Player.acquireBuilding`) is caught and turned into `ERR <verb> <message>`.

## Pick syntax

`EXECUTE` and `EXTRA_PICK` take card picks formatted as `<ROW>:<index>`:

- `TOP:0` — the leftmost card in the top row
- `BOTTOM:2` — the third card in the bottom row

`parsePick(String)` returns `null` on any malformed input; `parseInt` failures and unknown `VisibleRow` values both yield `ERR BAD_PICK <s>`. The parser is case-insensitive (`top:0` works too).

## Validation strategy

Two layers of validation:

1. **Controller-level pre-checks** — cheap, deterministic, fail fast:
   - **Phase guard** (every command): `game.getPhase() != expected` → `ERR <verb> wrong_phase=<phase>`. This prevents a rogue client from driving the Model out of sequence (e.g., calling EXECUTE during PLACEMENT).
   - `findPlayer(id)` → `ERR PLAYER_NOT_FOUND` if no such player
   - `findTile(idStr)` → `ERR TILE_NOT_FOUND` if no such tile (or unknown enum)
   - `player.getSelectedOfferTile() == null` → `ERR NO_TILE_SELECTED`
   - `parts.length < N` → `ERR <verb> NEEDS ...`
   - **Turn guard (EXECUTE)**: `game.getCurrentExecutionPlayer() != player` → `ERR EXECUTE not_your_turn`. Enforces left-to-right execution order on the offer track.
2. **Model-level deep checks** — the rules live in the Model:
   - [`OfferChoiceValidator`](../src/main/java/mesos/offer/OfferChoiceValidator.java) simulates the row state to catch over-picks and unaffordable building costs.
   - `Game.placeTotem` rejects null/occupied tiles and clears any previous occupancy.
   - `Player.acquireBuilding` throws if food can't cover the cost.

The Controller doesn't reimplement any rule. It just translates strings to Model calls and translates Model failures to error replies.

## Why a string-based command protocol

From the slides (Server 2', slide 46):

> `String processCmd(String s) {  // Business logic`

A text protocol is debuggable, requires no schema, and matches the slide deck verbatim. The cost is that arguments are positional and the parser is hand-written — fine at this scale.

The network transport uses Java object serialization (`CommandMessage` wraps the raw string), but the Controller itself only ever sees a `String`. This separation means the Controller can be tested without any network code: construct a `Game`, wrap it in a `Controller`, and call `processCmd("PLACE alice B")` directly in a JUnit test.

## What lives in the Controller vs. the Model

| Concern | Controller | Model |
|---|---|---|
| String parsing | ✅ | ❌ |
| Argument validation | ✅ (existence checks) | ✅ (semantic checks via `OfferChoiceValidator`) |
| Phase-transition logic | ❌ (guards the current phase; transitions are Model-driven) | ✅ (`Game.placeTotem` / `Game.markPlayerDone` auto-advance the phase) |
| Player turn order | ❌ | ✅ (caller of `PLACE` is responsible — Controller doesn't enforce) |
| Event resolution | ❌ | ✅ (`EventCard.resolveEvent`) |

Notably: **turn-order enforcement is not in the Controller.** A misbehaving client can `PLACE bob` before `alice`. In a real game this would be a coordination issue; the assumption is that clients cooperate and send commands in the correct order.

## Where to read

- [`Controller.java`](../src/main/java/mesos/controller/Controller.java) — under 200 lines, dispatch only.
- Compare to Cugola's reference: <https://github.com/ingconti/TowardsRemoteView>.
