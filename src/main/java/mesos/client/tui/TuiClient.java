package mesos.client.tui;

import java.util.List;
import java.util.Scanner;
import mesos.client.TcpTransport;
import mesos.client.Transport;
import mesos.db.ScoreResult;
import mesos.enums.TotemColor;
import mesos.network.client.CommandMessage;
import mesos.network.client.StartMessage;
import mesos.network.server.ErrMessage;
import mesos.network.server.HeartbeatMessage;
import mesos.network.server.InfoMessage;
import mesos.network.server.OkMessage;
import mesos.network.server.RankingMessage;
import mesos.network.server.SnapshotMessage;

/**
 * Text-based client for MESOS.
 *
 * <p>The view logic (rendering, command loop, hints) is fully decoupled from the
 * transport. {@link #main} defaults to TCP; {@link #start} accepts any
 * {@link Transport} so the {@code Launcher} can pass TCP or RMI.
 *
 * <p>Threading model:
 * <ul>
 *   <li>Main thread — reads stdin, sends {@code ClientMessage} via the transport.</li>
 *   <li>Transport's reader thread — delivers inbound {@code ServerMessage} to the
 *       {@code onMessage} callback registered in {@link #start}.</li>
 * </ul>
 * Both threads write to {@code System.out} and are synchronized on {@link #OUT_LOCK}.
 */
public class TuiClient {

    static final Object OUT_LOCK = new Object();

    private static volatile SnapshotMessage lastSnapshot = null;

    /** Default entry point — TCP on localhost:1234. */
    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int    port = args.length > 1 ? Integer.parseInt(args[1]) : 1234;
        start(new TcpTransport(), host, port);
    }

    /**
     * Starts the TUI with the given transport. Called by {@code Launcher} with
     * the transport already chosen (TCP or RMI) and the correct host/port.
     *
     * @param transport the network transport to use
     * @param host      server hostname or IP
     * @param port      server port (TCP: 1234, RMI: 1099)
     */
    public static void start(Transport transport, String host, int port) throws Exception {
        Scanner stdin = new Scanner(System.in);
        System.out.println("  [Tip: se ti stai riconnettendo a una partita crashata, usa il nickname che avevi prima]");
        System.out.print("Nickname: ");
        String nick = stdin.nextLine().trim();
        if (nick.isEmpty()) nick = "Player";
        final String finalNick = nick;

        boolean[] connected = {false};
        long[] lastMsg = {System.currentTimeMillis()};
        boolean[] executedThisRound = {false};

        transport.setOnMessage(msg -> {
            lastMsg[0] = System.currentTimeMillis();
            if (msg instanceof HeartbeatMessage) return;
            synchronized (OUT_LOCK) {
                // Print [connected] only on the first useful message from the server.
                // If the server sends ErrMessage("NICKNAME_TAKEN"), the flag stays false
                // and [connected] is never printed.
                if (!connected[0] && (msg instanceof OkMessage
                        || msg instanceof SnapshotMessage
                        || msg instanceof InfoMessage)) {
                    System.out.println("[connected] logged in as '" + finalNick + "'");
                    connected[0] = true;
                }
                if (msg instanceof InfoMessage info) {
                    System.out.println("[lobby] You are '" + info.getNick()
                            + "' — player " + info.getPlayerIndex() + "/" + info.getTotalPlayers());
                } else if (msg instanceof SnapshotMessage snap) {
                    lastSnapshot = snap;
                    if ("PLACEMENT".equals(snap.getPhase())) executedThisRound[0] = false;
                    BoardRenderer.render(snap, finalNick, executedThisRound[0]);
                    showHint(finalNick, executedThisRound[0]);
                    System.out.print("> ");
                } else if (msg instanceof OkMessage ok) {
                    if (ok.getText().startsWith("RECOVERY_JOINED")) {
                        String progress = ok.getText().substring("RECOVERY_JOINED ".length());
                        System.out.println("\n[recovery] Reconnecting to saved game — waiting for all players... (" + progress + ")");
                    } else {
                        System.out.println("\n[OK] " + ok.getText());
                    }
                } else if (msg instanceof RankingMessage rm) {
                    List<ScoreResult> entries = rm.getEntries();
                    int numP = entries.isEmpty() ? 0 : entries.get(0).numPlayers();
                    System.out.println("\n=== CLASSIFICA (" + numP + " players) =========");
                    for (int i = 0; i < entries.size(); i++) {
                        ScoreResult r = entries.get(i);
                        String marker = r.nickname().equals(finalNick) ? " <-- you" : "";
                        System.out.printf("  #%-3d %-20s %5d pts  (%d games)%s%n",
                                i + 1, r.nickname(), r.totalScore(), r.gamesPlayed(), marker);
                    }
                    System.out.println("===================================");
                    System.exit(0);
                } else if (msg instanceof ErrMessage err) {
                    if (err.getReason().contains("(connection lost)")) {
                        System.out.println("\n[game over] Server disconnected.");
                        System.exit(0);
                    } else if (err.getReason().startsWith("PLAYER_DISCONNECTED")) {
                        System.out.println("\n[game over] A player disconnected. The game has ended.");
                        System.exit(0);
                    } else if (err.getReason().startsWith("RECOVERY_TIMEOUT")) {
                        System.out.println("\n[game over] Recovery timed out — not all players reconnected.");
                        System.exit(0);
                    } else {
                        String reason = err.getReason();
                        // If EXECUTE or SKIP_EXTRA fail, the player can retry.
                        if (reason.startsWith("EXECUTE") || reason.startsWith("SKIP_EXTRA")) {
                            executedThisRound[0] = false;
                        }
                        System.out.println("\n[ERR] " + reason);
                    }
                }
            }
        });

        transport.connect(host, port, nick,
            () -> {
                int n = 0;
                while (n < 2 || n > 5) {
                    System.out.print("Number of players (2-5): ");
                    try { n = Integer.parseInt(stdin.nextLine().trim()); }
                    catch (NumberFormatException ignored) {
                        System.out.println("Please enter a number between 2 and 5.");
                    }
                }
                return n;
            },
            available -> {
                synchronized (OUT_LOCK) {
                    System.out.println("Available colors:");
                    for (int i = 0; i < available.size(); i++)
                        System.out.println("  " + (i + 1) + ") " + available.get(i));
                }
                int choice = -1;
                while (choice < 1 || choice > available.size()) {
                    System.out.print("Choose color (1-" + available.size() + "): ");
                    try { choice = Integer.parseInt(stdin.nextLine().trim()); }
                    catch (NumberFormatException ignored) { choice = -1; }
                    if (choice < 1 || choice > available.size())
                        System.out.println("  Invalid choice, enter a number between 1 and " + available.size() + ".");
                }
                return available.get(choice - 1);
            });
        lastMsg[0] = System.currentTimeMillis();
        Thread watchdog = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try { Thread.sleep(2_000); } catch (InterruptedException e) { return; }
                if (System.currentTimeMillis() - lastMsg[0] > 6_000) {
                    synchronized (OUT_LOCK) {
                        System.out.println("\n[game over] Server disconnected.");
                    }
                    transport.close();
                    System.exit(0);
                }
            }
        }, "watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        // ── Command loop ──────────────────────────────────────────────────────
        showHelp();
        while (true) {
            synchronized (OUT_LOCK) { System.out.print("> "); }
            if (!stdin.hasNextLine()) break;
            String line = stdin.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            String   verb  = parts[0].toLowerCase();

            switch (verb) {
                case "quit", "exit" -> { transport.close(); System.out.println("[disconnected]"); return; }
                case "help"         -> showHelp();
                case "board", "state" -> {
                    SnapshotMessage snap = lastSnapshot;
                    if (snap != null) BoardRenderer.render(snap, finalNick);
                    else synchronized (OUT_LOCK) { System.out.println("No snapshot available yet."); }
                }
                case "start"      -> sendObj(transport, new StartMessage());
                case "place" -> {
                    if (parts.length < 2)
                        System.out.println("Usage: place <tile>   e.g. place B");
                    else
                        sendCmd(transport, "PLACE " + finalNick + " " + parts[1].toUpperCase());
                }
                case "exec" -> {
                    StringBuilder sb = new StringBuilder("EXECUTE ").append(finalNick);
                    for (int i = 1; i < parts.length; i++) sb.append(" ").append(parts[i].toUpperCase());
                    sendCmd(transport, sb.toString());
                    executedThisRound[0] = true;
                }
                case "extrapick" -> {
                    if (parts.length < 2)
                        System.out.println("Usage: extrapick <TOP:n>   e.g. extrapick TOP:2");
                    else
                        sendCmd(transport, "EXTRA_PICK " + finalNick + " " + parts[1].toUpperCase());
                }
                case "skipextra" -> {
                    sendCmd(transport, "SKIP_EXTRA " + finalNick);
                    executedThisRound[0] = true;
                }
                default -> System.out.println("Unknown command: '" + verb + "'. Type 'help'.");
            }
        }

        transport.close();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Sends a raw command string via the transport and waits 200 ms.
     *
     * @param transport the active transport
     * @param cmd       the full command string (e.g. "PLACE alice B")
     */
    private static void sendCmd(Transport transport, String cmd) {
        synchronized (OUT_LOCK) { System.out.println("  -> " + cmd); }
        try {
            transport.send(new CommandMessage(cmd));
            Thread.sleep(200);
        } catch (Exception e) {
            System.err.println("[send error]: " + e.getMessage());
        }
    }

    private static void sendObj(Transport transport, Object msg) {
        synchronized (OUT_LOCK) { System.out.println("  -> " + msg.getClass().getSimpleName()); }
        try {
            transport.send((mesos.network.client.ClientMessage) msg);
            Thread.sleep(200);
        } catch (Exception e) {
            System.err.println("[send error]: " + e.getMessage());
        }
    }

    private static void showHint(String nick, boolean done) {
        SnapshotMessage snap = lastSnapshot;
        String phase = snap != null ? snap.getPhase() : "SETUP";

        synchronized (OUT_LOCK) {
            System.out.println();
            switch (phase) {
                case "SETUP" -> System.out.println("  [Type 'start' to begin the game]");

                case "PLACEMENT" -> {
                    if (snap == null) { System.out.println("  [place <tile>]"); break; }
                    String myTile = snap.getPlayers().stream()
                            .filter(p -> p.getId().equals(nick))
                            .map(SnapshotMessage.PlayerSnapshot::getSelectedTile)
                            .findFirst().orElse("-");
                    if (!"-".equals(myTile)) {
                        System.out.println("  [You placed on " + myTile + " — waiting for other players...]");
                    } else {
                        String currentPlacer = snap.getCurrentPlacementPlayerId();
                        if (currentPlacer != null && !currentPlacer.equals(nick)) {
                            System.out.println("  [Waiting for " + currentPlacer + " to place their totem...]");
                        } else {
                            String free = snap.getOfferTrack().stream()
                                    .filter(t -> "free".equals(t.getOccupant()))
                                    .map(SnapshotMessage.TileSnapshot::getTileId)
                                    .collect(java.util.stream.Collectors.joining(", "));
                            System.out.println("  [YOUR TURN to place — free tiles: " + free + "]");
                        }
                    }
                }

                case "ACTION_RESOLUTION" -> {
                    if (snap == null) { System.out.println("  [exec [TOP:n] [BOTTOM:m]]"); break; }
                    if (done) { System.out.println("  [Azione eseguita — aspetta gli altri giocatori...]"); break; }
                    String currentExecutor = snap.getCurrentExecutionPlayerId();
                    if (currentExecutor != null && !currentExecutor.equals(nick)) {
                        System.out.println("  [Waiting for " + currentExecutor + " to execute...]");
                        break;
                    }
                    String myTile = snap.getPlayers().stream()
                            .filter(p -> p.getId().equals(nick))
                            .map(SnapshotMessage.PlayerSnapshot::getSelectedTile)
                            .findFirst().orElse("-");
                    int top = tileTopBudget(myTile);
                    int bot = tileBotBudget(myTile);
                    StringBuilder sb = new StringBuilder("  [YOUR TURN — tile: ").append(myTile).append(" — exec");
                    if (top > 0) sb.append("  TOP:n (").append(top).append(top > 1 ? " cards from top row)" : " card from top row)");
                    if (bot > 0) sb.append("  BOTTOM:m (").append(bot).append(bot > 1 ? " cards from bottom row)" : " card from bottom row)");
                    if (top == 0 && bot == 0) sb.append(" (turn-order tile — no card pick)");
                    sb.append("]");
                    System.out.println(sb);
                }

                case "FINAL_SCORING",
                     "GAME_OVER" -> {
                    System.out.println("  [Partita finita.]");
                    // Fallback timer: if DB is not configured, RankingMessage never arrives,
                    // so exit after 3 seconds. If RankingMessage arrives first, System.exit(0)
                    // in its branch closes the process before this timer fires.
                    Thread exitTimer = new Thread(() -> {
                        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                        System.exit(0);
                    }, "exit-timer");
                    exitTimer.setDaemon(true);
                    exitTimer.start();
                }

                default -> System.out.println("  ['board' to redraw the board]");
            }
        }
    }

    private static int tileTopBudget(String tileId) {
        return switch (tileId) {
            case "C" -> 1; case "E" -> 1; case "F" -> 2; case "G" -> 2;
            default  -> 0;
        };
    }

    private static int tileBotBudget(String tileId) {
        return switch (tileId) {
            case "B" -> 1; case "D" -> 2; case "E" -> 1; case "G" -> 1;
            default  -> 0;
        };
    }

    private static void showHelp() {
        synchronized (OUT_LOCK) {
            System.out.println();
            System.out.println("  Available commands:");
            System.out.println("    start                          — start the game");
            System.out.println("    place <tile>                   — place your totem   e.g. place B");
            System.out.println("    exec [TOP:n ...]               — execute your action e.g. exec TOP:0");
            System.out.println("    extrapick <TOP:n>              — extra pick from top row");
            System.out.println("    skipextra                      — skip extra pick");
            System.out.println("    state / board                  — redraw the board");
            System.out.println("    help                           — show this message");
            System.out.println("    quit                           — disconnect");
        }
    }
}