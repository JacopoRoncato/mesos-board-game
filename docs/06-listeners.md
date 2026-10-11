# 06 — Listener mechanism

> 🇮🇹 Versione italiana: [`it/06-listener.md`](it/06-listener.md)

How the Model talks to the View without knowing it exists.

## The pattern: `java.beans.PropertyChangeListener`

Two interfaces from `java.beans`:

```java
public interface PropertyChangeListener {
    void propertyChange(PropertyChangeEvent evt);
}

public class PropertyChangeEvent {
    Object getSource();
    String getPropertyName();
    Object getOldValue();
    Object getNewValue();
}
```

Standard since Java 1.4. Used throughout Swing internally. The slides (50–66) walk through it explicitly.

**Why this pattern**, per the slides:

> A) Per esempio vogliamo poter aggiungere ascoltatori senza modificare il codice della sorgente delle modifiche.
> B) non è necessario tenere una lista dei potenziali interessati alle modifiche.
> C) disaccoppiare le modifiche al modello dalle view che devono mostrare le modifiche.

Translation:
- (A) You can add new listeners without modifying the source of changes.
- (B) No need to maintain a list of "things that might be interested".
- (C) Decouples Model changes from Views that display them.

In our case, the Model fires events; the `ModelListener` (wired by `StartMessage`) is the sole listener. But the Model doesn't know that — if we wanted to add a logging listener or a replay-recording listener, no Model code would change.

## The listener chain on the server

There is one listener system on the server side:

**`java.beans.PropertyChangeListener`** carries Model state changes to `ModelListener`, whose `CallBack` lambda calls `ServerRegistry.broadcast(SnapshotMessage.from(game))`. Every connected client then receives the full game state as a `SnapshotMessage` via their `VirtualView`.

## Where Model listeners live

Two classes carry a listener field:

| Class | Why |
|---|---|
| [`Game`](../src/main/java/mesos/game/Game.java) | Phase / round / era — the "big" temporal state |
| [`Player`](../src/main/java/mesos/player/Player.java) | Food / PP — per-player resource state |

Other classes (`Board`, `Tribe`, `OfferTile`, `TurnOrderTile`) don't fire events. They could — the pattern is mechanical to extend (see *How to extend* below).

## How `setListener` propagates

A single call from `StartMessage.process()`:

```java
newCtrl.setListener(new ModelListener(evt ->
    reg.broadcast(SnapshotMessage.from(game))
));
```

flows down:

```
StartMessage.process()
   └─ Controller.setListener(modelListener)
        └─ Game.setListener(modelListener)
             ├─ this.listener = modelListener
             └─ for each player: player.setListener(modelListener)   ← propagation
```

The Controller forwards (no storage). The Game stores AND propagates. Players store. After the chain runs once, every state-mutating operation in the Model can fire an event by calling its local `listener.propertyChange(...)`.

Note: `setListener` is called **after** `controller.processCmd("START")` in `StartMessage.process()`. This is intentional — setup events (FOOD, PHASE fired during game initialization) are silently dropped, avoiding a burst of intermediate snapshots. Only one clean snapshot is broadcast after the game is fully initialized.

If Players were added to the Game **after** `setListener` (e.g., dynamic player joining), they wouldn't have the listener. In the current bootstrap this can't happen — players are constructed from the registered nicks before the Game is created.

## Firing events: the `fire` helper pattern

Both `Game` and `Player` use the same idiom:

```java
private PropertyChangeListener listener;

public void setListener(PropertyChangeListener listener) {
    this.listener = listener;
    // ...propagate if needed...
}

private void fire(String prop, Object oldValue, Object newValue) {
    if (listener != null) {
        listener.propertyChange(new PropertyChangeEvent(this, prop, oldValue, newValue));
    }
}
```

Three lines. The null check matters — before `setListener` runs (during `Game` construction, and during the initial `processCmd("START")` call), the listener is null and firing is a no-op.

Each mutator fires after the state change:

```java
public void gainFood(int amount) {
    if (amount > 0) {
        food += amount;
        fire();
    }
}
```

`Game` wraps phase/era/round changes in private setters with a guard to prevent spurious events:

```java
private void setPhase(GamePhase newPhase) {
    GamePhase old = this.phase;
    this.phase = newPhase;
    if (old != newPhase) fire();
}
```

The guard prevents an extra snapshot broadcast when a setter is called with the same value.

## Property naming convention

Both `Game.fire()` and `Player.fire()` use a single fixed property name `"update"` with `oldValue=null` and `newValue=null`:

```java
listener.propertyChange(new PropertyChangeEvent(this, "update", null, null));
```

The `CallBack` lambda ignores the event content entirely — it always broadcasts a full `SnapshotMessage` regardless of what changed. The property name and old/new values carry no semantic information in the current implementation.

If a future listener needed to know *what* changed (e.g., a GUI that animates only the modified player's food bar), each mutator would need to pass the specific property name and old/new values into `fire()` — the pattern supports it, but the current code does not use it.

## The `StartMessage` side

`StartMessage.process()` constructs the `ModelListener` with a `CallBack` lambda:

```java
newCtrl.setListener(new ModelListener(evt ->
    reg.broadcast(SnapshotMessage.from(game))
));
```

The lambda's body is the only thing that knows "events become SnapshotMessage broadcasts". Everything else is pure pattern. The `evt` parameter is received but not inspected — every event, regardless of what changed, triggers a full snapshot.

If you wanted to also log events to a file: register a second listener that writes to the file. The Model wouldn't change.

## On the client side

The TUI client has no listener system. The reader thread:
1. Receives a `SnapshotMessage` object via `ObjectInputStream.readObject()`.
2. Stores it in `static volatile SnapshotMessage lastSnapshot`.
3. Calls `BoardRenderer.render(snapshot)` directly.

A future **Swing GUI client** would follow the standard Swing pattern:
```java
// in the reader thread (daemon):
Object obj = in.readObject();
if (obj instanceof SnapshotMessage snap) {
    SwingUtilities.invokeLater(() -> {
        // update panels from snap
    });
}
```

No additional listener system is needed — the `SnapshotMessage` is the complete state, so panels can re-render directly from it.

## A full event trace (PLACE command)

```
Client sends:  CommandMessage("PLACE alice B")
                      │
                      ▼
TcpClientHandler enqueues into BlockingQueue
                      │
                      ▼
ControllerWorker dequeues, calls CommandMessage.process(controller)
                      │
                      ▼
Controller.processCmd("PLACE alice B")
                      │
                      ▼
Game.placeTotem(alice, tileB)
                      │
                      ▼
totem moved → Game fires some internal events
                      │
(listener is now wired — each fire() triggers:)
                      ▼
ModelListener.propertyChange(evt)
                      │
                      ▼
callBack.gotEvent(evt)
                      │
                      ▼
ServerRegistry.broadcast(SnapshotMessage.from(game))
                      │
                      ├─ TcpVirtualView(alice).sendMessage(snapshot)  → alice's socket
                      └─ TcpVirtualView(bob).sendMessage(snapshot)    → bob's socket

Back in CommandMessage.process():
    sender.sendMessage(new OkMessage("PLACE alice -> B"))  → alice's socket only
```

Both clients receive the full board state. Only alice receives the ACK.

## How to extend

To fire events from a new class (say, `Board`):

1. Add `private PropertyChangeListener listener;` field.
2. Add `public void setListener(PropertyChangeListener)` setter.
3. Add `private void fire(String prop, Object old, Object new)` helper (copy from `Game`).
4. In `Game.setListener`, also forward to `board.setListener(listener)`.
5. In each mutating method of `Board`, capture old state and call `fire(...)`.

Five steps, mechanical. The current `CallBack` lambda ignores event content and broadcasts a full snapshot every time — so extending the event set costs nothing on the server side.

## Limitations of `PropertyChangeListener`

- **Single source per event** — the event carries one source object. For events that conceptually span multiple objects (e.g., "row state changed" — which involves multiple slots), you either fire one event per slot or invent a composite event object.
- **No threading guarantees** — events fire on whatever thread mutated the Model. In this architecture, that's always the `ControllerWorker` thread (single consumer), so `broadcast` is always called from one thread. Safe for `ConcurrentHashMap` iteration.
- **No backpressure** — `ObjectOutputStream.writeObject` is buffered by the OS; if a client is slow, the buffer fills and `writeObject` blocks. In a multi-client server with one slow client, this could delay `broadcast` for all other clients. For the course's scope, this is not a concern.
