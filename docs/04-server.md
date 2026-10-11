# 04 — Server-side View

> 🇮🇹 Versione italiana: [`it/04-server.md`](it/04-server.md)

The server-side half of the View. Two transports are supplied; both live in [`mesos.server`](../src/main/java/mesos/server) and plug into the same `BlockingQueue` and `ServerRegistry`.

## TCP — class roles

| Class | What it does |
|---|---|
| [`ServerMain`](../src/main/java/mesos/server/ServerMain.java) | TCP/RMI entrypoint. Creates the shared `BlockingQueue` and `ServerRegistry`, starts `ControllerWorker`, opens the TCP `ServerSocket`, and starts the RMI registry. Runs until killed. |
| [`TcpClientHandler`](../src/main/java/mesos/server/TcpClientHandler.java) | Per-client daemon thread. Handles the login handshake, creates a `TcpVirtualView`, registers it in `ServerRegistry`, then enqueues subsequent `ClientMessage` objects in the `BlockingQueue`. |
| [`ControllerWorker`](../src/main/java/mesos/server/ControllerWorker.java) | Single daemon thread. Drains the `BlockingQueue<ClientMessage>`. For each message: calls `msg.setRegistry(registry)` then `msg.process(controller)`. Single-threaded → Controller access is naturally serialized without locks. |
| [`ServerRegistry`](../src/main/java/mesos/server/ServerRegistry.java) | Central state store. `ConcurrentHashMap<nick, VirtualView>`, `AtomicInteger expectedPlayers`, `AtomicReference<Controller>`. Provides `tryRegister`, `broadcast`, `handleDisconnection`. |
| [`ModelListener`](../src/main/java/mesos/server/ModelListener.java) | `implements PropertyChangeListener`. Bridges the standard Java Beans listener interface to the project's `CallBack` functional interface. |
| [`CallBack`](../src/main/java/mesos/server/CallBack.java) | `@FunctionalInterface` (`gotEvent(PropertyChangeEvent)`). Decouples the listener from the transport. Wired by `StartMessage` as a lambda that calls `registry.broadcast(SnapshotMessage.from(game))`. |

The VirtualView **interface** and its implementations (`TcpVirtualView`, `RmiVirtualView`) live in [`mesos.server`](../src/main/java/mesos/server), alongside the rest of the server infrastructure.

## Why split `ModelListener` and `CallBack`

The slides (62–65) introduce both, and the split is worth keeping:

- `ModelListener` is the standard Java contract — it implements `PropertyChangeListener`, so it can be registered with any `PropertyChangeListener`-aware object.
- `CallBack` is the *transport* — what to do with an event when one arrives.

By making `ModelListener` take a `CallBack` in its constructor, you can swap the transport (broadcast a snapshot, log to file, fan out to multiple subscribers, push over RMI) without rewriting the listener.

## `ServerMain` — the entrypoint

```java
public static void main(String[] args) throws Exception {
    int tcpPort = args.length > 0 ? Integer.parseInt(args[0]) : 1234;
    int rmiPort = args.length > 1 ? Integer.parseInt(args[1]) : 1099;

    BlockingQueue<ClientMessage> queue    = new LinkedBlockingQueue<>();
    ServerRegistry               registry = new ServerRegistry();

    // Start the single-threaded queue consumer
    Thread worker = new Thread(new ControllerWorker(queue, registry), "controller-worker");
    worker.setDaemon(true);
    worker.start();

    // Start the TCP accept loop (one TcpClientHandler thread per connected client)
    ServerSocket serverSocket = new ServerSocket(tcpPort);
    Thread tcpAccept = new Thread(() -> acceptLoop(serverSocket, queue, registry), "tcp-accept");
    tcpAccept.setDaemon(true);
    tcpAccept.start();

    // Start the RMI registry (optional — non-fatal if it fails)
    RemoteGameServiceImpl rmiService = new RemoteGameServiceImpl(queue, registry);
    LocateRegistry.createRegistry(rmiPort).rebind("mesos-game", rmiService);

    Thread.currentThread().join(); // block main thread forever
}
```

What's configurable:
- TCP port via `args[0]` (default `1234`)
- RMI port via `args[1]` (default `1099`)
- MySQL host via `args[2]` (e.g. `localhost` or `localhost:3306`) — optional, enables FA1 leaderboard
- MySQL user via `args[3]` (default `root`)
- MySQL password via `args[4]` (default `""`)
- Player count is NOT set here — it is negotiated during the lobby handshake.

Example with DB enabled:
```bash
java -cp target/classes mesos.server.ServerMain 1234 1099 localhost root Mesos_2026_SQL
```

The server runs indefinitely. There is no "one game per server lifetime" limit.

## `TcpClientHandler` — per-client TCP thread

```java
@Override
public void run() {
    String registeredNick = null;
    try (socket) {
        // OOS must be created before OIS to avoid header-exchange deadlock
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

        // ── Login handshake ──────────────────────────────────────────────
        Object first = in.readObject();
        // must be LoginMessage; validate player count, tryRegister nick
        ...
        registeredNick = login.getNick();

        // ── Game message loop ────────────────────────────────────────────
        while (true) {
            Object obj = in.readObject();
            if (!(obj instanceof ClientMessage)) continue;
            ClientMessage msg = (ClientMessage) obj;
            msg.setSender(view);
            queue.put(msg);
        }

    } catch (IOException e) { /* client disconnected */ }
    finally {
        if (registeredNick != null) registry.handleDisconnection(registeredNick);
    }
}
```

Three things happen here:
1. **Lobby + colour handshake (three steps).** First `LoginMessage` carries only the nick (`numPlayers=0`). `tryRegister` enforces nick uniqueness via `putIfAbsent`. If `expectedPlayers == -1` (first client), the handler replies `OkMessage("LOBBY_FIRST")` and reads a second `LoginMessage` carrying `n ∈ [2,5]`, then sets `expectedPlayers` and replies `OkMessage("LOBBY_ACCEPTED")`. Subsequent clients receive `OkMessage("LOBBY_JOINED n/m")`. After the lobby step, **all** clients go through the colour handshake: the server sends `InfoMessage(nick, playerIndex, totalPlayers, availableColors)`, the client replies with `ColorMessage(requestedColor)` (or `null` for auto-assign), and the server replies `OkMessage("COLOR_ACCEPTED <color>")`. Only then does the handler enter the game message loop. On recovery (see FA2 below), `handleRecovery()` is called instead of `handleLobby()` — it validates the nick against the saved game and forces the previously-assigned colour.
2. **Message loop.** Every subsequent `ClientMessage` gets its `sender` field set to this client's `TcpVirtualView`, then goes into the queue.
3. **Disconnection cleanup.** Whether the socket closes cleanly or throws `IOException`, `registry.handleDisconnection(nick)` removes the player and broadcasts `ErrMessage("PLAYER_DISCONNECTED nick")` to the remaining clients.

## `ControllerWorker` — the serialization point

```java
@Override
public void run() {
    while (!Thread.currentThread().isInterrupted()) {
        ClientMessage msg = queue.take();     // blocks until a message arrives
        msg.setRegistry(registry);
        msg.process(registry.getController()); // null before StartMessage has run
    }
}
```

Single thread → no lock needed on the Controller or the Model. This is the core of the "Server 2'" approach: move all command processing onto one thread via a queue, and the concurrency problem disappears.

`msg.process(controller)` may receive a `null` controller (before any `StartMessage` has been processed). Each `ClientMessage` implementation handles this case:
- `StartMessage` checks `controller != null` and returns `ErrMessage("GAME_ALREADY_STARTED")`.
- `CommandMessage` checks `controller == null` and returns `ErrMessage("GAME_NOT_STARTED")`.

## `ServerRegistry` — the shared state store

```java
public class ServerRegistry {
    private final Map<String, VirtualView>     views         = new ConcurrentHashMap<>();
    private final AtomicInteger                expectedPlayers = new AtomicInteger(-1);
    private final AtomicReference<Controller>  controllerRef = new AtomicReference<>(null);
    private final AtomicBoolean                terminated    = new AtomicBoolean(false);
    private volatile Thread                    heartbeatThread;      // interrupted on disconnect

    // FA1 — leaderboard DB
    private ScoreRepository repository;          // null if DB not configured
    public void setRepository(ScoreRepository r) { this.repository = r; }

    // FA2 — game persistence / recovery
    private Game savedGame;
    private GamePersistence persistence;
    public void setSavedGame(Game g) { this.savedGame = g; }
    public boolean isRecovering() { return savedGame != null; }
    public void checkRecoveryComplete() { /* resumes game when all players reconnected */ }

    public void setExpectedPlayers(int n) { expectedPlayers.compareAndSet(-1, n); }
    public boolean tryRegister(String nick, VirtualView view) { ... putIfAbsent ... }
    public void unregister(String nick) { views.remove(nick); }  // lobby cleanup, no broadcast
    public void handleDisconnection(String nick) {               // idempotent via AtomicBoolean
        if (!terminated.compareAndSet(false, true)) return;
        views.remove(nick); broadcast(new ErrMessage("PLAYER_DISCONNECTED " + nick));
        controllerRef.set(null); views.clear();
        if (heartbeatThread != null) heartbeatThread.interrupt(); // stop keep-alive
    }
    public boolean isFull() { ... views.size() >= expectedPlayers ... }
    public void broadcast(ServerMessage msg) { views.values().forEach(v -> v.sendMessage(msg)); }

    // Heartbeat — started by ServerMain at startup
    public void startHeartbeat() {
        heartbeatThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try { Thread.sleep(2_000); } catch (InterruptedException e) { return; }
                broadcast(new HeartbeatMessage());
            }
        }, "heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }
}
```

`ConcurrentHashMap` and atomic primitives are used because multiple `TcpClientHandler` threads may call `tryRegister` and `setExpectedPlayers` concurrently during the lobby phase. Once the game starts, only `ControllerWorker` drives the registry (via `broadcast`), so further synchronization is not needed.

## `StartMessage` — the game bootstrap

`StartMessage.process(controller)` is the most complex `ClientMessage`. It:

1. Checks `registry.isFull()` — rejects early with `NOT_ENOUGH_PLAYERS n/m` if not all expected players are connected.
2. Builds `Game` + `Controller` from the registered nicks.
3. Calls `controller.processCmd("START")` **without a listener** — so setup events (FOOD, PHASE) are silently dropped, avoiding snapshot spam.
4. Wires the `ModelListener` now, so future commands trigger broadcast:
   ```java
   newCtrl.setListener(new ModelListener(evt ->
       reg.broadcast(SnapshotMessage.from(game))
   ));
   ```
5. Publishes the controller to `registry.setController(newCtrl)` — this unblocks `ControllerWorker` for subsequent `CommandMessage` objects.
6. Broadcasts one clean `SnapshotMessage` to all clients + sends `OkMessage("GAME_STARTED players=...")` to the sender.

## Snapshot broadcasting

Every Model mutation triggers the `CallBack` lambda wired in step 4 above:
```java
evt -> reg.broadcast(SnapshotMessage.from(game))
```

`SnapshotMessage.from(game)` captures the full game state at the moment of the event. All connected clients receive the same snapshot via their respective `VirtualView.sendMessage(snapshot)` calls. The sender additionally receives an `OkMessage` or `ErrMessage`.

## VirtualView interface and implementations

The `VirtualView` interface (in `mesos.server`) is the outbound channel to one client:

```java
public interface VirtualView {
    void sendMessage(ServerMessage msg);
}
```

Two implementations:

| Class | Transport | How it sends |
|---|---|---|
| [`TcpVirtualView`](../src/main/java/mesos/server/TcpVirtualView.java) | TCP | Calls `ObjectOutputStream.writeObject(msg)` inside `synchronized(out)`. If the write throws `IOException` (e.g. the client crashed between heartbeats), calls `registry.handleDisconnection(nick)` immediately. |
| [`RmiVirtualView`](../src/main/java/mesos/server/RmiVirtualView.java) | RMI | Calls `RemoteGameCallback.onServerMessage(msg)`. On `RemoteException` (client crash/disconnect), calls `registry.handleDisconnection(nick)` — same cleanup as TCP. |

`TcpClientHandler` creates `TcpVirtualView(out, nick, registry)` after a successful login and registers it in `ServerRegistry`.

**Thread-safety note for `TcpVirtualView`:** the `ObjectOutputStream` is shared between the `TcpClientHandler` thread (which writes lobby replies during handshake) and the `ControllerWorker` thread (which calls `broadcast` during the game). `sendMessage` is therefore `synchronized(out)` to prevent interleaved writes that would corrupt the stream.

## RMI variant

[`RemoteGameServiceImpl`](../src/main/java/mesos/server/RemoteGameServiceImpl.java) implements [`RemoteGameService`](../src/main/java/mesos/server/RemoteGameService.java) (bound as `"mesos-game"` in the RMI registry):

- `login(nick, callback)` — registers the nick, returns `"LOBBY_FIRST"` or `"LOBBY_JOINED n/m"`. Throws `RemoteException("NICKNAME_TAKEN nick")` if the nick is already in use.
- `setNumPlayers(n)` — called by the first RMI client after receiving `"LOBBY_FIRST"`; sets `expectedPlayers`. Throws `RemoteException` if `n ∉ [2,5]`.
- `sendMessage(msg, nick)` — sets `msg.sender = registry.get(nick)` and enqueues into the same `BlockingQueue` as TCP.

This means TCP and RMI clients can participate in the **same game** — the `ControllerWorker` processes both transparently.

**InfoMessage:** after the lobby handshake, `TcpClientHandler` pushes an `InfoMessage(nick, playerIndex, totalPlayers)` to the client over TCP. For RMI, no server push happens at login time — `RmiTransport.connect()` synthesizes the `InfoMessage` locally from the `"LOBBY_FIRST"` / `"LOBBY_JOINED n/m"` status string so that `TuiClient`'s `onMessage` callback sees it identically regardless of transport.

**RMI client: implemented as `RmiTransport`.** [`RmiTransport`](../src/main/java/mesos/client/RmiTransport.java)
extends `UnicastRemoteObject` and implements both `Transport` and `RemoteGameCallback`.
It looks up `RemoteGameService` from the registry and calls `login(nick, numPlayers, this)`,
passing itself as the callback stub. See [`05-client.md`](05-client.md) for details.

## Disconnection handling

Disconnection can be detected from two directions:

- **Reader-side (TcpClientHandler):** catches `IOException` in the game message loop → calls `registry.handleDisconnection(registeredNick)`.
- **Writer-side (TcpVirtualView):** if `writeObject` throws `IOException` during a broadcast (e.g. heartbeat reaches a dead client) → calls `registry.handleDisconnection(nick)` directly.

Both paths converge on the same `handleDisconnection`, which is idempotent via `AtomicBoolean`:

1. Removes the disconnected nick from the `ConcurrentHashMap`.
2. Broadcasts `ErrMessage("PLAYER_DISCONNECTED nick")` to all remaining clients.
3. Sets `controllerRef` to `null` — subsequent commands receive `ErrMessage("GAME_NOT_STARTED")`.
4. Clears the view map — future broadcasts are no-ops.
5. Interrupts the heartbeat thread.

**The game terminates for all connected clients.** Remaining clients receive `PLAYER_DISCONNECTED` and exit (the TUI calls `System.exit(0)`).

## Heartbeat

`ServerMain` calls `registry.startHeartbeat()` before accepting any TCP connections. The heartbeat thread sends a `HeartbeatMessage` to all connected clients every **2 seconds** via `broadcast`. Clients use this to distinguish a live server from a crashed one (see [05 — Client](05-client.md) §watchdog).

`HeartbeatMessage` carries no data — it is a sentinel object used only as a keep-alive signal. It is skipped by `TcpTransport.readHandshake()` during the synchronous login phase so that heartbeats intercalated between lobby messages do not break the handshake sequence.

When `handleDisconnection` is called for any reason, it interrupts the heartbeat thread so it stops broadcasting to an empty view map.

## FA1 — Leaderboard on DB (`mesos.db`)

When `args[2]` is provided, `ServerMain` instantiates `ScoreRepository(mysqlHost, user, pass)`:

1. Connects to MySQL without a database and executes `CREATE DATABASE IF NOT EXISTS GC43_Mesos_DB` — idempotent, safe to run on every startup.
2. Reconnects to `GC43_Mesos_DB` and runs `schema.sql` (`CREATE TABLE IF NOT EXISTS game_results`).
3. Registers the repository in `ServerRegistry` via `setRepository(repo)`.

At game over, `ServerRegistry` calls `repo.saveResults(ranking, numPlayers)` and then broadcasts `RankingMessage(repo.getRanking(numPlayers))` to all clients.

| Class | Role |
|---|---|
| `ScoreRepository` | Opens connection, creates DB/table, `saveResults`, `getRanking` |
| `ScoreResult` | Aggregated leaderboard row (nick, totalScore, numPlayers, gamesPlayed) |

## FA2 — Game persistence and recovery (`GamePersistence`)

`GamePersistence` serializes the `Game` object to a file (`mesos-save.dat`) after every Model mutation and reloads it on server startup.

Recovery flow:
1. `ServerMain` calls `persistence.load()` — if a save exists, it calls `registry.setSavedGame(game)`.
2. `isRecovering()` returns `true` while not all players have reconnected.
3. `TcpClientHandler` calls `handleRecovery(nick, view, in)` instead of `handleLobby` — validates the nick against the saved game, forces the previously-assigned colour, and registers the view.
4. When the last expected player reconnects, `checkRecoveryComplete()` resumes the game from the saved state.

## File reference

- [`ServerMain.java`](../src/main/java/mesos/server/ServerMain.java)
- [`TcpClientHandler.java`](../src/main/java/mesos/server/TcpClientHandler.java)
- [`ControllerWorker.java`](../src/main/java/mesos/server/ControllerWorker.java)
- [`ServerRegistry.java`](../src/main/java/mesos/server/ServerRegistry.java)
- [`GamePersistence.java`](../src/main/java/mesos/server/GamePersistence.java) — FA2 serialization
- [`ModelListener.java`](../src/main/java/mesos/server/ModelListener.java)
- [`CallBack.java`](../src/main/java/mesos/server/CallBack.java)
- [`RemoteGameServiceImpl.java`](../src/main/java/mesos/server/RemoteGameServiceImpl.java)
- [`ScoreRepository.java`](../src/main/java/mesos/db/ScoreRepository.java) — FA1 DB access
