# UML diagrams

PlantUML source files, one per architectural area. Each `.puml` is self-contained and renders to SVG/PNG via any PlantUML-aware tool.

---

## Index (branch `delivery` — aggiornato 2026-06-23)

### Architecture
- [`01-architecture-overview.puml`](01-architecture-overview.puml) — component diagram: Client / Network / Server, percorso comandi e percorso snapshot annotati

### Sequence diagrams
- [`02-sequence-login-start.puml`](02-sequence-login-start.puml) — sequenza TCP completa: two-step lobby handshake (LoginMessage → LOBBY_FIRST/LOBBY_JOINED/LOBBY_ACCEPTED), StartMessage, prima broadcast di SnapshotMessage; include il path NICKNAME_TAKEN
- [`03-sequence-command.puml`](03-sequence-command.puml) — sequenza: elaborazione di un CommandMessage generico dopo lo start (PLACE, END_ROUND, ecc.)

### Class diagrams — infrastructure
- [`04-network-class-diagram.puml`](04-network-class-diagram.puml) — package `mesos.network.server` (ServerMessage, OkMessage, ErrMessage, InfoMessage, HeartbeatMessage, RankingMessage, SnapshotMessage con `totemColor` in PlayerSnapshot) e `mesos.network.client` (ClientMessage, LoginMessage, ColorMessage, StartMessage, CommandMessage); VirtualView + TcpVirtualView + RmiVirtualView; RemoteGameCallback
- [`05-server-class-diagram.puml`](05-server-class-diagram.puml) — package `mesos.server`: ServerMain, ServerRegistry (con `terminated`, `heartbeatThread`, FA1 `repository`, FA2 `savedGame`/`persistence`, `claimColor`), TcpClientHandler (lobby + colour handshake + recovery), ControllerWorker, ModelListener, CallBack, TcpVirtualView (con `registry`), RmiVirtualView, RemoteGameService (`login()` → String, `setNumPlayers()`, `getAvailableColors()`, `claimColor()`), RemoteGameServiceImpl, GamePersistence (FA2); package `mesos.db`: ScoreRepository, ScoreResult (FA1)
- [`06-rmi-diagram.puml`](06-rmi-diagram.puml) — RMI transport: `RemoteGameService` (`login(nick, callback) : String`, `setNumPlayers(int)`), `RemoteGameServiceImpl`, `RmiVirtualView`; lato client: `RemoteGameCallback`, `RmiTransport` (`connect()` con `Supplier<Integer>`, `send()` gestisce `RemoteException`)

### Class diagrams — model
- [`07-model-class-diagram.puml`](07-model-class-diagram.puml) — model alto livello: Game, Board, Player, Tribe, Totem, OfferTrack, TurnOrderTile, gerarchia Card, BuildingEffect

### Conceptual diagrams
- [`08-mvc-distributed.puml`](08-mvc-distributed.puml) — pattern MVC distribuito "Server 2'" (Cugola): flusso comandi e flusso snapshot, pattern Observer / Command / Proxy / Producer-Consumer

### State diagrams
- [`10-state-gamephase.puml`](10-state-gamephase.puml) — state diagram: transizioni di `GamePhase` (SETUP → PLACEMENT → ACTION_RESOLUTION → END_OF_ROUND → loop → FINAL_SCORING → GAME_OVER)

### Class diagrams — model (dettaglio)
- [`11-card-hierarchy.puml`](11-card-hierarchy.puml) — gerarchia carte: `CharacterCard` (6 tipi), `EventCard` (4 tipi), `BuildingCard`, `FinalEventCard`, `BuildingEffect` (8 effetti, pattern Strategy)
- [`12-controller-class-diagram.puml`](12-controller-class-diagram.puml) — package `mesos.controller`: `Controller` (processCmd, setListener, cmd*); `StateSerializer` rimosso dal codebase
- [`13-client-class-diagram.puml`](13-client-class-diagram.puml) — package `mesos.client`, `mesos.client.tui`, `mesos.client.gui`, `mesos.client.ui`: `Transport` (con `askColor`), `TcpTransport`, `RmiTransport`, `Launcher`, `TuiClient`, `BoardRenderer`, `AutoPlayer`; `GuiClient`, `GameSnapshot`, `PhaseController`, `GuiPhase`; pannelli chiave `BoardPanel`/`PlayerPanel`/`StatusBar`/`ActionPanel`/`LobbyPanel`

### Sequence diagrams — dettaglio
- [`14-sequence-disconnection.puml`](14-sequence-disconnection.puml) — gestione disconnessione TCP e RMI: `handleDisconnection()` idempotente (`AtomicBoolean terminated`), nullifica controller, svuota views; path client TCP (`TcpTransport` reader → ErrMessage → System.exit); path client RMI (`send()` cattura RemoteException → ErrMessage → System.exit)
- [`15-sequence-rmi-flow.puml`](15-sequence-rmi-flow.puml) — flusso RMI completo con two-step login: lookup → `login(nick, callback)` → `LOBBY_FIRST` → `setNumPlayers(n)` → sendMessage → queue → ControllerWorker → Game mutation → broadcast → `RmiVirtualView.onServerMessage` → `BoardRenderer.render`

### Use case diagrams
- [`16-use-case-diagram.puml`](16-use-case-diagram.puml) — attori (Giocatore, Primo Giocatore) e casi d'uso: login, impostazione numero giocatori, avvio partita, posizionamento totem, acquisizione carta, ricezione snapshot, disconnessione

### Activity diagrams
- [`17-activity-game-round.puml`](17-activity-game-round.puml) — ciclo di gioco completo: lobby → setup → PLACEMENT (PLACE per ogni giocatore) → ACTION_RESOLUTION (EXECUTE per ogni giocatore) → END_OF_ROUND → loop o FINAL_SCORING → GAME_OVER

### Deployment diagrams
- [`18-deployment-diagram.puml`](18-deployment-diagram.puml) — topologia di rete: nodo server (JVM + ServerMain/Registry/ControllerWorker/Game) collegato a 1..5 nodi client (JVM + TUI/GUI + TCP o RMI)

### Class diagrams — GUI client (dettaglio)
- [`19-gui-class-diagram.puml`](19-gui-class-diagram.puml) — architettura interna GUI: `GuiClient` (JFrame + CardLayout + 5 card), `GameSnapshot` (model locale con listener), `PhaseController` (state machine → `GuiPhase`), pannelli `mesos.client.ui` (BoardPanel, PlayerPanel, StatusBar, ActionPanel, LobbyPanel, EndGamePanel, DisconnectPanel, CountPanel, RowPanel, OfferTrackPanel, CardComponent, OfferTileComponent, TurnOrderTilePanel, TotemBadge, ResourceCounter); note su invariante EDT, cave-art full-bleed, `setOpaque`

### Sequence diagrams — GUI client
- [`20-sequence-gui-snapshot.puml`](20-sequence-gui-snapshot.puml) — flusso completo GUI: (1) SnapshotMessage arriva al network reader thread → `invokeLater` → `GameSnapshot.update` → listener → `repaint` sui pannelli; (2) click bottone sull'EDT → `transport.send(CommandMessage)` → server round-trip; (3) watchdog daemon → `showDisconnect` su timeout 6 s

---

## Changelog diagrammi

| Data | Diagrammi | Motivo |
|---|---|---|
| 2026-06-19 | 19 | Corretto per allinearlo al codice reale: `GuiPhase` aggiunto `PLACEMENT_MINE`; `PhaseController` corretto (costruttore, `getGuiPhase()`, `getRemainingPicks()`, `update(SnapshotMessage)`, rimossi metodi inesistenti); `GameSnapshot` corretto (`applySnapshot()`, `phase : Phase`, enum nested `Phase`, rimosso `offerChoices`); `PlayerState.nick` → `id`; `CardInfo.kind : CardComponent.Kind`; `PicksRemaining` marcato `<<record>>` con `total()`/`decTop()`/`decBottom()`; relazione `PhaseController→GameSnapshot` corretta in `→SnapshotMessage`; nota "Nine states" → "Ten states". |
| 2026-06-07 | 13, 19 (nuovo), 20 (nuovo) | GUI Swing implementata: `13` aggiornato con package `gui/` e `ui/`; `19` nuovo class diagram dettagliato GUI; `20` nuovo sequence diagram SnapshotMessage → EDT → repaint + button → send. |
| 2026-06-05 | 04, 05, 17, 18 | FA1: `RankingMessage`, `ScoreRepository`, `ScoreResult` aggiunti (04, 05, 17, 18). FA2: `GamePersistence` aggiunto (05, 18). Heartbeat: `HeartbeatMessage`, `startHeartbeat()`, watchdog client aggiunti (04, 05, 18). Colour handshake: `ColorMessage`, `InfoMessage` aggiunti (04, 05). Login flow aggiornato in 17 (two-step + colore). `getAvailableColors`/`claimColor` in `RemoteGameService` (05). |
| 2026-06-03 | 02, 14 | `02`: aggiunta scelta colore totem (ColorMessage → OkMessage("COLOR_ACCEPTED"), InfoMessage con `availableColors`, note su `readHandshake()`). `14`: aggiunta sezioni heartbeat (2s, broadcast a tutti), rilevamento client TCP morto via `TcpVirtualView` IOException, watchdog TuiClient (6s timeout → `System.exit`); aggiunto `heartbeatThread.interrupt()` in `handleDisconnection` |
| 2026-05-25 | 02, 04, 05, 06, 12, 13, 14, 15 | Two-step lobby handshake; split `mesos.network` in `server/client`; `EventMessage` rimossa; `totemColor` in `PlayerSnapshot`; `ServerRegistry.terminated` + `unregister()`; `login()` restituisce `String`; `setNumPlayers()` aggiunto; `Transport.connect()` con `Supplier<Integer>`; disconnessione TCP+RMI lato client; `StateSerializer` rimosso |

---

## How to render

### Option A — PlantUML CLI

```bash
java -jar plantuml.jar -tsvg Derivables/uml/*.puml
```

For PNG: `-tpng`. For LaTeX: `-tlatex`.

### Option B — VS Code

Install the **PlantUML** extension by jebbs. Open any `.puml` and press `Alt+D` to preview.

### Option C — IntelliJ IDEA

Install the **PlantUML Integration** plugin. The diagram preview panel appears next to the source.

### Option D — Online

Paste the file contents into <https://www.plantuml.com/plantuml/uml/> for an instant render.

### Option E — GitHub

GitHub doesn't render PlantUML natively. Options:
1. Commit the rendered `.svg` / `.png` alongside the `.puml` and embed with `![](rendered.svg)`
2. Use the public PlantUML server: `![](https://www.plantuml.com/plantuml/svg/...)` (base64-encoded source)
