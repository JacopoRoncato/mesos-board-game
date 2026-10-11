# 01 — Architecture

> 🇮🇹 Versione italiana: [`it/01-architettura.md`](it/01-architettura.md)

## The MVC split

MESOS implements the **"Server 2'" variante** from Gian Enrico Conti's *Towards Remote View* slide deck (slides 43–66), extended to **multi-client** with a shared command queue. The split:

| Layer | Lives on | Purpose |
|---|---|---|
| **Model** | Server | Pure game state + business logic. No I/O. No knowledge of network or UI. |
| **Controller** | Server | Parses textual commands from `CommandMessage` objects, validates, dispatches to the Model. Owns the Model. Access serialized via `ControllerWorker`. |
| **View** | Two halves | **Virtual View** (server-side, per-client): outbound channel that ships `ServerMessage` objects back to the client. **Client View** (TUI or Swing GUI): displays state, sends user actions as `CommandMessage` objects. |

The Model has **no knowledge** of the Controller or the View. Communication from Model → View flows through `java.beans.PropertyChangeListener`. The `StartMessage` wires a `ModelListener` into the Controller, which broadcasts a full `SnapshotMessage` to all clients after every Model mutation.

Two transports are supplied side-by-side:

- **TCP**, using Java object serialization (`ObjectOutputStream` / `ObjectInputStream`). Default.
- **RMI**, a parallel transport in [`mesos.server`](../src/main/java/mesos/server) that reuses the same `BlockingQueue` and `ServerRegistry`.

Both transports funnel through the same `ControllerWorker`; the Model never knows which one is in use.

## The full data flow (TCP / TUI)

```
USER TYPES COMMAND IN TUI (e.g. "place alice B")
        │
        ▼
TuiClient wraps it: new CommandMessage("PLACE alice B")
        │
        ▼
ObjectOutputStream.writeObject(commandMessage)
        │
   [TCP socket / object serialization]
        │
        ▼
TcpClientHandler.run() reads ClientMessage via ObjectInputStream
        │  sets msg.sender = TcpVirtualView (for this client)
        ▼
BlockingQueue<ClientMessage>.put(msg)
        │
        ▼
ControllerWorker.run() takes from queue  [single thread — no locks needed]
        │  calls msg.setRegistry(registry)
        │  calls msg.process(controller)
        ▼
CommandMessage.process(controller)       [double-dispatch / visitor]
        │
        ▼
Controller.processCmd("PLACE alice B")
        │
        ▼
Game.placeTotem(...)   [Model mutation]
        │
        ▼ [Model fires PropertyChangeEvent]
        │
        ▼
ModelListener.propertyChange(evt)
        │
        ▼
CallBack lambda (wired by StartMessage):
    reg.broadcast(SnapshotMessage.from(game))
        │
        ▼
ServerRegistry iterates all VirtualViews → TcpVirtualView.sendMessage(snapshot)
        │
   [TCP socket / object serialization]
        │
        ▼
TuiClient reader thread reads SnapshotMessage via ObjectInputStream
        │
        ▼
BoardRenderer.render(snapshot)  → prints ASCII board to stdout
```

After the broadcast, `CommandMessage.process()` also sends an `OkMessage` or `ErrMessage` to the **sender only** via `msg.sender`.

**Key insight:** Every Model mutation broadcasts a full `SnapshotMessage` to **all** connected clients. There are no partial updates. `EVT` lines are never sent on the wire.

## Why this design (Cugola's rationale)

From the slides:

> "(A) Per esempio vogliamo poter aggiungere ascoltatori senza modificare il codice della sorgente delle modifiche.
> (B) non è necessario tenere una lista dei potenziali interessati alle modifiche.
> (C) disaccoppiare le modifiche al modello dalle view che devono mostrare le modifiche."

Three benefits:
1. The Model doesn't need to know how many views exist or who's interested.
2. Adding a new observer doesn't require editing the Model.
3. Model and View are decoupled — the Model can evolve without breaking the View, and vice versa.

A direct call from `StartMessage` → `Game` would couple them and violate the Observer contract.

## Process model

- **Server (TCP accept loop)**: a daemon thread runs `ServerSocket.accept()` in a loop. For each accepted connection it spawns a `TcpClientHandler` daemon thread.
- **Server (ControllerWorker)**: a single daemon thread drains `BlockingQueue<ClientMessage>`. Single-threaded access means the Controller needs no synchronization.
- **Server (RMI)**: handled by the RMI runtime, which threads incoming calls automatically. `RemoteGameServiceImpl.sendMessage` enqueues into the same `BlockingQueue` as TCP.
- **TUI client**: three threads after `connect()`.
  - **Main thread** — reads stdin, sends `ClientMessage` objects.
  - **Reader thread** (daemon) — delivers `ServerMessage` to the `onMessage` callback, synchronises output via `OUT_LOCK`.
  - **Watchdog** (daemon) — exits with `System.exit(0)` if no message arrives for 6 s (server crash detection).
- **GUI client**: network reader + EDT + watchdog daemon.
  - **Network reader thread** — reads `ServerMessage`; every message is forwarded via `SwingUtilities.invokeLater` so the EDT performs all Swing mutations.
  - **EDT** (Swing Event Dispatch Thread) — updates `GameSnapshot`, notifies listeners, repaints panels, dispatches button actions.
  - **Watchdog** (daemon) — same 6 s crash detection as TUI.

The reader/EDT split on the GUI client is identical in principle to the TUI two-thread model: the transport thread never touches UI components directly.

## Package map (key classes)

| Concern | Class |
|---|---|
| Game orchestrator (round / era / phase) | `Game` |
| Final-score math | `ScoreCalculator` |
| Board aggregate (rows, decks, offer track, turn order) | `Board` |
| Tribe deck construction (per Era) | `TribeDeckFactory` |
| Building deck construction (per Era) | `BuildingDeckFactory` |
| One player's tribe (cards + delegations) | `Tribe` |
| Character-side math | `CharacterStats` |
| Building-side aggregation + hooks | `BuildingEffects` |
| Offer Tile + choice validation | `OfferTile`, `OfferChoiceValidator` |
| Controller dispatch | `Controller` |
| VirtualView interface (outbound channel to one client) | `VirtualView` |
| TCP implementation of VirtualView | `TcpVirtualView` |
| RMI implementation of VirtualView | `RmiVirtualView` |
| Central server registry (nick → VirtualView, Controller) | `ServerRegistry` |
| Per-client TCP thread | `TcpClientHandler` |
| Single-threaded queue consumer | `ControllerWorker` |
| PropertyChangeListener → CallBack bridge | `ModelListener` |
| Text-based client | `TuiClient` |
| ASCII board renderer | `BoardRenderer` |
| Automated smoke-test client | `AutoPlayer` |
| Swing GUI main controller | `GuiClient` |
| Client-side game state mirror | `GameSnapshot` |
| Local phase state machine | `PhaseController` |

## Where to read the source

| Area | Folder | Entry point |
|---|---|---|
| Model | [`mesos.game`](../src/main/java/mesos/game), [`mesos.board`](../src/main/java/mesos/board), [`mesos.cards`](../src/main/java/mesos/cards), [`mesos.player`](../src/main/java/mesos/player), [`mesos.tribe`](../src/main/java/mesos/tribe), [`mesos.effects`](../src/main/java/mesos/effects), [`mesos.offer`](../src/main/java/mesos/offer), [`mesos.order`](../src/main/java/mesos/order) | `Game.java` |
| Controller | [`mesos.controller`](../src/main/java/mesos/controller) | `Controller.java` |
| Network messages | [`mesos.network`](../src/main/java/mesos/network) | `ClientMessage.java`, `ServerMessage.java` |
| TCP server | [`mesos.server`](../src/main/java/mesos/server) | `ServerMain.java` |
| RMI server | [`mesos.server`](../src/main/java/mesos/server) | `RemoteGameServiceImpl.java` |
| TUI client | [`mesos.client.tui`](../src/main/java/mesos/client/tui) | `TuiClient.java` |
| GUI controller | [`mesos.client.gui`](../src/main/java/mesos/client/gui) | `GuiClient.java` |
| GUI panels | [`mesos.client.ui`](../src/main/java/mesos/client/ui) | `BoardPanel.java` |
| RMI client | [`mesos.client`](../src/main/java/mesos/client) | `RmiTransport.java` (Transport + RemoteGameCallback) |
| JUnit tests | [`src/test/java/mesos`](../src/test/java/mesos) | per-package test classes |

## Next docs

- [02 — Model](02-model.md)
- [03 — Controller](03-controller.md)
- [04 — Server-side View](04-server.md)
- [05 — Client-side View](05-client.md)
- [06 — Listeners](06-listeners.md)
- [07 — Wire protocol](07-protocol.md)
- [08 — Build & run](08-build-run.md)
