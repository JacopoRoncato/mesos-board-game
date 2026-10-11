# 08 — Build & run

> 🇮🇹 Versione italiana: [`it/08-build-run.md`](it/08-build-run.md)

## Prerequisites

- **JDK 17 or newer.** Validated on OpenJDK 25.0.2.
- **Maven 3.x** (optional — works with raw `javac` too).
- A TCP port available (default `1234` for TCP; default `1099` for RMI).

Check your install:

```bash
java --version
mvn --version
```

If `mvn` is missing, you can still build with `javac` (instructions below).

---

## Build via Maven

From the project root (where `pom.xml` lives):

```bash
mvn -DskipTests package
```

This produces `Derivables/mesos.jar` (fat JAR, no extra dependencies needed) and `target/classes/`. There are no third-party dependencies, so the first run will be quick.

The `pom.xml` currently sets:
```xml
<maven.compiler.source>17</maven.compiler.source>
<maven.compiler.target>17</maven.compiler.target>
```

If you're on a newer JDK and want bytecode parity, edit these to match.

---

## Build via `javac` (no Maven)

If Maven isn't available:

```bash
# From the project root (macOS / Linux)
mkdir -p target/classes
javac -d target/classes -sourcepath src/main/java $(find src/main/java -name "*.java")
```

On Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force -Path target\classes | Out-Null
$files = Get-ChildItem -Recurse src\main\java -Filter *.java | ForEach-Object { $_.FullName }
javac -d target\classes -encoding UTF-8 @files
```

---

## Run the TCP server

```bash
# Without DB (leaderboard disabled)
java -cp Derivables/mesos.jar mesos.server.ServerMain

# With custom ports
java -cp Derivables/mesos.jar mesos.server.ServerMain 9000 1099

# With MySQL leaderboard (FA1) — creates GC43_Mesos_DB automatically if missing
java -cp Derivables/mesos.jar mesos.server.ServerMain 1234 1099 localhost root Mesos_2026_SQL
```

Args: `[tcpPort] [rmiPort] [mysqlHost] [dbUser] [dbPass]`
- `mysqlHost` accepts `localhost` or `localhost:3306` (custom port)
- The DB name `GC43_Mesos_DB` is fixed in code — do not pass it as argument

The server prints:
```
[tcp] in ascolto sulla porta 1234
[rmi] registry avviato sulla porta 1099
[db] database GC43_Mesos_DB verificato/creato   ← only if mysqlHost provided
[db] connesso a GC43_Mesos_DB su localhost
```

The server accepts multiple clients concurrently. The **first client** sets the expected player count via `LoginMessage.numPlayers`; subsequent clients send `numPlayers=0`. The server runs indefinitely.

---

## Run the client (Launcher — recommended)

The unified entry point lets you choose interface (TUI / GUI) and transport (TCP / RMI)
interactively at startup:

```bash
# localhost, default port
java -jar Derivables/mesos.jar

# remote server, default port
java -jar Derivables/mesos.jar 192.168.1.5

# remote server, explicit port
java -jar Derivables/mesos.jar 192.168.1.5 9000

# RMI with explicit client IP override (args[2], only if auto-detection fails)
java -jar Derivables/mesos.jar 192.168.1.5 1099 192.168.1.10
```

Port defaults are transport-specific: TCP → 1234, RMI → 1099.

**First client** (sets player count):
```
Select interface:
  1) TUI  (text)
  2) GUI  (Swing)
> 1
Select transport:
  1) TCP  (socket)
  2) RMI
> 1
Nickname: alice
Number of players (2-5): 2
Available colors:
  1) RED
  2) BLUE
  3) WHITE
  4) YELLOW
  5) PURPLE
Choose color (1-5): 1
[connected] logged in as 'alice'
```

**Subsequent clients** (no player-count prompt):
```
Select interface: ...
Select transport: ...
Nickname: bob
Available colors:
  1) BLUE
  2) WHITE
  3) YELLOW
  4) PURPLE
Choose color (1-4): 1
[lobby] You are 'bob' (2/2 players)
```

---

## Run the TUI client directly (TCP only, legacy entry point)

Bypasses the Launcher and always uses TCP:

```bash
java -cp Derivables/mesos.jar mesos.client.tui.TuiClient
# or to a remote server:
java -cp Derivables/mesos.jar mesos.client.tui.TuiClient 192.168.1.5 9000
```

Type `help` to see all available commands. Type `start` once all expected players are connected.

---

## Run the AutoPlayer (automated smoke test)

```bash
java -cp Derivables/mesos.jar mesos.client.tui.AutoPlayer
# or to a remote server:
java -cp Derivables/mesos.jar mesos.client.tui.AutoPlayer 192.168.1.5 9000
```

Args: `[host] [port] [nick] [numPlayers] [startDelayMs]` — all optional.

Connects with an auto-generated nick (`auto_XXXX`), waits **5 seconds** for a second client to join, then drives all players through all 10 rounds automatically. Run two instances simultaneously — the first becomes active (drives the game), the second becomes passive (observes).

```bash
# Terminal 1 — server
java -cp Derivables/mesos.jar mesos.server.ServerMain

# Terminal 2 — first AutoPlayer (active, starts after 5 s)
java -cp Derivables/mesos.jar mesos.client.tui.AutoPlayer

# Terminal 3 — second AutoPlayer (passive, must join within 5 s)
java -cp Derivables/mesos.jar mesos.client.tui.AutoPlayer
```

---

## Run the RMI client

The RMI registry is started automatically by `ServerMain`. To connect a client via RMI,
use the Launcher and select option `2` at the transport prompt:

```bash
java -jar Derivables/mesos.jar
# > 1  (TUI)
# > 2  (RMI)
# Nickname: alice
# Number of players (2-5): 2   ← first client only
# Choose color (1-5): 1
```

Default RMI port: 1099. To specify a custom port:
```bash
java -jar Derivables/mesos.jar 127.0.0.1 1099
```

A TCP client and an RMI client can play in the **same game** — the server handles both
transports through the same `BlockingQueue` and `ControllerWorker`.

---

## End-to-end smoke test (2-player, TUI)

### TCP only

**Terminal 1 — server:**
```bash
java -cp Derivables/mesos.jar mesos.server.ServerMain
```

**Terminal 2 — first client (alice, TCP):**
```bash
java -jar Derivables/mesos.jar
# > 1 (TUI)  > 1 (TCP)
# Nickname: alice  |  Number of players (2-5): 2  |  Choose color: 1
```

**Terminal 3 — second client (bob, TCP):**
```bash
java -jar Derivables/mesos.jar
# > 1 (TUI)  > 1 (TCP)
# Nickname: bob  |  Choose color: 1
```

### Cross-transport (TCP + RMI)

**Terminal 2 — alice via TCP:**
```bash
java -jar Derivables/mesos.jar
# > 1 (TUI)  > 1 (TCP)  → port 1234
# Nickname: alice  |  Number of players (2-5): 2  |  Choose color: 1
```

**Terminal 3 — bob via RMI:**
```bash
java -jar Derivables/mesos.jar
# > 1 (TUI)  > 2 (RMI)  → port 1099
# Nickname: bob  |  Choose color: 1
```

**Back in either terminal — type `start`:**
```
> start
```

Both clients should immediately receive a `SnapshotMessage` and print the ASCII board.
If you see the board in both terminals, every layer is working: serialization,
`BlockingQueue`, `ControllerWorker`, `StartMessage`, `ModelListener`, `ServerRegistry.broadcast`.

Then step through a full round:
```
> place B
> place C          (other client)
(server auto-advances to ACTION_RESOLUTION)
> exec TOP:0
> skipextra
> exec TOP:0       (other client)
> skipextra        (other client)
(server auto-advances to next PLACEMENT)
```

Both clients receive a new `SnapshotMessage` after each command. Phase transitions (PLACEMENT → ACTION_RESOLUTION, ACTION_RESOLUTION → next PLACEMENT) happen automatically — no explicit `beginact` or `endround` is needed.

---

## Troubleshooting

### "Address already in use" on server start
Another process is on port 1234 (or 1099 for RMI). Either find it (`netstat -ano | findstr 1234` on Windows, `lsof -i :1234` on macOS/Linux) and stop it, or start the server on a different port (`ServerMain 9001`) and connect the client to the new port.

### Client shows "Connection failed" or hangs at `ObjectInputStream` creation
- Is the server running? (`jps` or check the server terminal)
- Right host and port?
- Firewall blocking the port?
- Remember: `ObjectOutputStream` must be created and flushed **before** `ObjectInputStream` on **both sides**. If the server blocks on `new ObjectInputStream(...)` and the client also blocks, it's a header-exchange deadlock — `out.flush()` after creating `ObjectOutputStream` is required.

### `InvalidClassException` or `ClassNotFoundException` at deserialization
Client and server were compiled from different source versions. The `serialVersionUID` values don't match. Recompile both from the same source with `mvn -DskipTests package`.

### Server prints `[rmi] non disponibile: ...` and continues
The RMI registry could not start (e.g., port 1099 already in use). TCP still works normally. Use a different RMI port: `ServerMain 1234 1100`.

### `Compilation error: invalid source release: 17`
Your `JAVA_HOME` is pointing to JDK 8/11. Set it to JDK 17+:

```bash
export JAVA_HOME=/path/to/jdk17               # macOS/Linux
$env:JAVA_HOME = "C:\Path\To\jdk-25"          # PowerShell
```

### `Could not find or load main class …`
Wrong classpath or missing JAR. Make sure `Derivables/mesos.jar` exists (run `mvn -DskipTests package` first). Use `-cp Derivables/mesos.jar` for the server and AutoPlayer, and `-jar Derivables/mesos.jar` for the Launcher.

### `[ERR] PLACE_FAILED <tile>`
The tile is already occupied. The server is also defensive: `PLACE alice B` followed by `PLACE alice C` will free B and occupy C.

### `[ERR] EXECUTE …`
Your picks didn't match the tile's allowance, or include a building you can't afford. Type `board` to re-render the board, then re-issue with the correct `TOP:i` / `BOTTOM:i` picks.

---

## IDE setup (IntelliJ)

1. **Open** the project root as a Maven project — IntelliJ auto-detects `pom.xml`.
2. **Project SDK**: 17 or newer (25 recommended).
3. **Run configurations**: create as many as you use:
   - `mesos.server.ServerMain` (no args needed for defaults)
   - `mesos.client.Launcher` (optional args: `127.0.0.1` — prompts interactively)
   - `mesos.client.tui.TuiClient` (optional args: `127.0.0.1 1234` — TCP only, legacy)
   - `mesos.client.tui.AutoPlayer` (optional args: `127.0.0.1 1234`)
4. **Run multiple configs simultaneously** — IntelliJ lets you run server + two clients in separate tabs.

