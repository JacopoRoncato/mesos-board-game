package mesos.client.tui;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import mesos.network.client.ColorMessage;
import mesos.network.client.CommandMessage;
import mesos.network.client.LoginMessage;
import mesos.network.client.StartMessage;
import mesos.db.ScoreResult;
import mesos.network.server.ErrMessage;
import mesos.network.server.OkMessage;
import mesos.network.server.RankingMessage;
import mesos.network.server.ServerMessage;
import mesos.network.server.SnapshotMessage;
import mesos.network.server.SnapshotMessage.PlayerSnapshot;
import mesos.network.server.SnapshotMessage.SlotSnapshot;
import mesos.network.server.SnapshotMessage.TileSnapshot;

/**
 * Automated single-connection client that plays a full game without human input.
 *
 * Connects as the "first" player (supplies numPlayers=2), starts the game, then
 * drives all players through every round by reading each SnapshotMessage and
 * computing the correct PLACE / EXECUTE picks before sending the next command.
 *
 * Usage:
 *   java mesos.client.tui.AutoPlayer [host] [port]
 *   Defaults: 127.0.0.1 / 1234
 *
 * The second player must already be connected (e.g. the background-alice script).
 */
public class AutoPlayer {

    // Tile pick budgets (cardsFromTop, cardsFromBottom) — fixed by MESOS rules.
    private static int tileTop(String tileId) {
        return switch (tileId) {
            case "C" -> 1;
            case "E" -> 1;
            case "F" -> 2;
            case "G" -> 2;
            default  -> 0;
        };
    }

    private static int tileBottom(String tileId) {
        return switch (tileId) {
            case "B" -> 1;
            case "D" -> 2;
            case "E" -> 1;
            case "G" -> 1;
            default  -> 0;
        };
    }

    /** Entry point. Optional args: host, port, nick, numPlayers, startDelayMs. */
    public static void main(String[] args) throws Exception {
        String host         = args.length > 0 ? args[0] : "127.0.0.1";
        int    port         = args.length > 1 ? Integer.parseInt(args[1]) : 1234;
        String nick         = args.length > 2 ? args[2] : "auto_" + (System.currentTimeMillis() % 9000 + 1000);
        int    numPlayers   = args.length > 3 ? Integer.parseInt(args[3]) : 2;
        int    startDelayMs = args.length > 4 ? Integer.parseInt(args[4]) : 5000;

        Socket socket = new Socket(host, port);
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

        // Step 1: login without numPlayers
        out.writeObject(new LoginMessage(nick, 0));
        out.flush();

        // Step 2: synchronous lobby handshake (reader thread not started yet)
        Object lobbyResp = in.readObject();
        if (lobbyResp instanceof ErrMessage lobbyErr) {
            System.err.println("[auto] errore lobby: " + lobbyErr.getReason());
            socket.close();
            return;
        }
        boolean isFirst = lobbyResp instanceof OkMessage ok2 && ok2.getText().equals("LOBBY_FIRST");
        if (isFirst) {
            out.writeObject(new LoginMessage(nick, numPlayers));
            out.flush();
            in.readObject(); // LOBBY_ACCEPTED
        }
        // Colour handshake: read InfoMessage, send ColorMessage(null) for auto-assign
        in.readObject(); // InfoMessage — available colours, ignored by AutoPlayer
        out.writeObject(new ColorMessage(null)); // auto-assign first available colour
        out.flush();
        in.readObject(); // COLOR_ACCEPTED — ignored

        System.out.println("[auto] connesso come " + nick + (isFirst ? " (primo — guida la partita)" : " (passivo — solo osserva)"));

        // ── Reader thread — collects the latest snapshot ──────────────────────
        final SnapshotMessage[] snap = {null};
        final boolean[] rankingReceived = {false};
        final Object lock = new Object();

        Thread reader = new Thread(() -> {
            try {
                while (true) {
                    Object obj = in.readObject();
                    if (obj instanceof SnapshotMessage s) {
                        synchronized (lock) { snap[0] = s; lock.notifyAll(); }
                        BoardRenderer.render(s);
                    } else if (obj instanceof RankingMessage rm) {
                        List<ScoreResult> entries = rm.getEntries();
                        int numP = entries.isEmpty() ? 0 : entries.get(0).numPlayers();
                        System.out.println("\n=== CLASSIFICA (" + numP + " players) ===");
                        for (int i = 0; i < entries.size(); i++) {
                            ScoreResult r = entries.get(i);
                            String marker = r.nickname().equals(nick) ? " <-- you" : "";
                            System.out.printf("  #%-3d %-20s %5d pts  (%d games)%s%n",
                                    i + 1, r.nickname(), r.totalScore(), r.gamesPlayed(), marker);
                        }
                        System.out.println("===================================");
                        synchronized (lock) { rankingReceived[0] = true; lock.notifyAll(); }
                        System.exit(0);
                    } else if (obj instanceof OkMessage ok) {
                        System.out.println("[OK] " + ok.getText());
                    } else if (obj instanceof ErrMessage err) {
                        System.out.println("[ERR] " + err.getReason());
                    }
                }
            } catch (Exception ignored) {}
        }, "auto-reader");
        reader.setDaemon(true);
        reader.start();

        // ── Helpers ───────────────────────────────────────────────────────────

        // Blocks until a snapshot with the given phase name arrives (or 6 s timeout).
        java.util.function.Predicate<String> waitPhase = phaseName -> {
            synchronized (lock) {
                try {
                    long deadline = System.currentTimeMillis() + 6_000;
                    while (snap[0] == null || !snap[0].getPhase().equals(phaseName)) {
                        long rem = deadline - System.currentTimeMillis();
                        if (rem <= 0) return false;
                        lock.wait(rem);
                    }
                    return true;
                } catch (InterruptedException e) { return false; }
            }
        };

        // Sends a command and waits 250 ms.
        java.util.function.Consumer<String> send = cmd -> {
            System.out.println("  -> " + cmd);
            try {
                out.writeObject(new CommandMessage(cmd));
                out.flush();
                Thread.sleep(250);
            } catch (Exception e) { System.err.println("[errore] " + e.getMessage()); }
        };

        // Waits until player `playerId` has selectedTile="-" in the snapshot
        // (semantic proof that the server has processed their EXECUTE).
        // More robust than reference comparison: avoids false positives from intermediate
        // snapshots generated by multi-phase effects of other players.
        java.util.function.BiPredicate<String, Long> waitPlayerDone = (playerId, deadlineMs) -> {
            synchronized (lock) {
                try {
                    while (true) {
                        SnapshotMessage cur = snap[0];
                        if (cur != null) {
                            boolean done = cur.getPlayers().stream()
                                    .filter(pp -> pp.getId().equals(playerId))
                                    .anyMatch(pp -> "-".equals(pp.getSelectedTile()));
                            if (done) return true;
                        }
                        long rem = deadlineMs - System.currentTimeMillis();
                        if (rem <= 0) return false;
                        lock.wait(rem);
                    }
                } catch (InterruptedException e) { return false; }
            }
        };

        // Returns the first n CHARACTER slot indices in a row.
        java.util.function.BiFunction<List<SlotSnapshot>, Integer, List<Integer>>
                pickChars = (row, n) -> {
            List<Integer> idxs = new ArrayList<>();
            for (int i = 0; i < row.size() && idxs.size() < n; i++) {
                if ("CHARACTER".equals(row.get(i).getKind())) idxs.add(i);
            }
            return idxs;
        };

        // Passive mode: non-first client connects and waits for the game to end.
        if (!isFirst) {
            System.out.println("[auto] modalità passiva — in attesa della fine partita...");
            reader.join(); // blocca fino a quando il server chiude la connessione
            socket.close();
            return;
        }

        // ── Game loop ─────────────────────────────────────────────────────────

        System.out.println("[auto] attendo tutti i giocatori e invio START...");
        Thread.sleep(startDelayMs); // wait for all players to connect
        out.writeObject(new StartMessage());
        out.flush();
        Thread.sleep(600);

        for (int round = 1; round <= 10; round++) {
            System.out.println("\n=== ROUND " + round + " ===");

            // ── PLACEMENT ────────────────────────────────────────────────────
            SnapshotMessage placement;
            synchronized (lock) { placement = snap[0]; }
            if (placement == null || !placement.getPhase().equals("PLACEMENT")) {
                System.out.println("[auto] attendo PLACEMENT...");
                if (!waitPhase.test("PLACEMENT")) { System.out.println("[auto] timeout PLACEMENT"); break; }
                synchronized (lock) { placement = snap[0]; }
            }

            // Place each player on a free tile in order.
            List<TileSnapshot> tiles = placement.getOfferTrack();
            int tileIdx = 0;
            for (PlayerSnapshot p : placement.getPlayers()) {
                if ("-".equals(p.getSelectedTile())) {
                    while (tileIdx < tiles.size() && !"free".equals(tiles.get(tileIdx).getOccupant()))
                        tileIdx++;
                    if (tileIdx < tiles.size()) {
                        send.accept("PLACE " + p.getId() + " " + tiles.get(tileIdx).getTileId());
                        tileIdx++;
                    }
                }
            }

            // ── ACTION_RESOLUTION ─────────────────────────────────────────────
            if (!waitPhase.test("ACTION_RESOLUTION")) { System.out.println("[auto] timeout ACTION_RESOLUTION"); break; }
            SnapshotMessage action;
            synchronized (lock) { action = snap[0]; }

            List<PlayerSnapshot> players = action.getPlayers();
            for (PlayerSnapshot p : players) {
                String tileId = p.getSelectedTile();
                if ("-".equals(tileId)) continue;

                int needTop    = tileTop(tileId);
                int needBottom = tileBottom(tileId);

                List<Integer> topPicks    = pickChars.apply(action.getTopRow(),    needTop);
                List<Integer> bottomPicks = pickChars.apply(action.getBottomRow(), needBottom);

                StringBuilder cmd = new StringBuilder("EXECUTE ").append(p.getId());
                for (int i : topPicks)    cmd.append(" TOP:").append(i);
                for (int i : bottomPicks) cmd.append(" BOTTOM:").append(i);
                String pid = p.getId();
                send.accept(cmd.toString());
                // Handle ExtraTopRowPickEffect: SKIP_EXTRA is idempotent when the effect is absent.
                send.accept("SKIP_EXTRA " + pid);
                // Wait for the server to confirm the execute: player's selectedTile → "-"
                waitPlayerDone.test(pid, System.currentTimeMillis() + 4_000);
                synchronized (lock) { action = snap[0]; }
            }

            // END_ROUND removed: phase advances automatically when all players have executed.
            // Wait for the snapshot to leave ACTION_RESOLUTION (→ PLACEMENT or GAME_OVER).
            Thread.sleep(800);
            SnapshotMessage current;
            synchronized (lock) { current = snap[0]; }
            if (current != null && ("GAME_OVER".equals(current.getPhase())
                    || "FINAL_SCORING".equals(current.getPhase()))) {
                System.out.println("[auto] partita terminata al round " + round);
                break;
            }
        }

        // wait up to 4 s for the server to send RankingMessage (requires DB configured)
        synchronized (lock) {
            long deadline = System.currentTimeMillis() + 4_000;
            while (!rankingReceived[0]) {
                long rem = deadline - System.currentTimeMillis();
                if (rem <= 0) break;
                try { lock.wait(rem); } catch (InterruptedException e) { break; }
            }
            if (!rankingReceived[0]) {
                // DB not configured — fall back to snapshot scores
                System.out.println("\n[auto] classifica finale (nessun DB):");
                SnapshotMessage final_ = snap[0];
                if (final_ != null) {
                    for (PlayerSnapshot p : final_.getPlayers()) {
                        System.out.printf("  %-12s  pp=%d  cibo=%d%n", p.getId(), p.getPrestigePoints(), p.getFood());
                    }
                }
            }
        }

        socket.close();
        System.exit(0);
    }
}
