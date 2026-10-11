package mesos.server;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import mesos.controller.Controller;
import mesos.db.ScoreRepository;
import mesos.enums.GamePhase;
import mesos.enums.TotemColor;
import mesos.game.Game;
import mesos.network.server.ErrMessage;
import mesos.network.server.HeartbeatMessage;
import mesos.network.server.RankingMessage;
import mesos.network.server.ServerMessage;
import mesos.network.server.SnapshotMessage;

/**
 * Central server-side registry.
 *
 * Responsibilities:
 *  - Tracks nick → VirtualView for all connected clients.
 *  - Stores the expected player count (set by the first login).
 *  - Holds the AtomicReference to the Controller (null until StartMessage creates it).
 *  - Broadcasts ServerMessage objects to all registered views.
 */
public class ServerRegistry {
    private ScoreRepository repository = null;
    private volatile Game savedGame = null; // game saved to disk
    private GamePersistence persistence = null; // associated game persistence

    private final Map<String, VirtualView>  views        = new ConcurrentHashMap<>();
    // map to store chosen colours keyed by nickname
    private final Map<String, TotemColor>   chosenColors = new ConcurrentHashMap<>();
    // number of players expected to connect (provided by the first LoginMessage)
    private final AtomicInteger expectedPlayers   = new AtomicInteger(-1);
    private final AtomicReference<Controller> controllerRef = new AtomicReference<>(null);
    private final AtomicBoolean terminated = new AtomicBoolean(false); // whether the game has ended
    private volatile Thread heartbeatThread;

    // compareAndSet sets to n only if expectedPlayers is still -1; otherwise no-op
    public void setExpectedPlayers(int n) {
        expectedPlayers.compareAndSet(-1, n);
    }

    public int getExpectedPlayers() { return expectedPlayers.get(); }

    public void register(String nick, VirtualView view) {
        views.put(nick, view); // client connects and receives a VirtualView
        System.out.println("[lobby] " + nick + " connected ("
                + views.size() + "/" + expectedPlayers + " players)");
    }

    /**
     * Registers the view only if the nick is not already taken.
     * Uses putIfAbsent for atomicity under concurrent connections.
     *
     * @return true if registered successfully, false if nick already in use
     */
    // registers only if the nickname is not already taken
    public boolean tryRegister(String nick, VirtualView view) {
        if (views.putIfAbsent(nick, view) != null) return false;
        System.out.println("[lobby] " + nick + " connected ("
                + views.size() + "/" + expectedPlayers + " players)");
        return true;
    }
    // handle disconnection of a player
    public void handleDisconnection(String nick) {
        if (!terminated.compareAndSet(false, true)) return; // game already terminated
        views.remove(nick); // remove the player
        System.out.println("[server] " + nick + " disconnected — game terminated");
        broadcast(new ErrMessage("PLAYER_DISCONNECTED " + nick)); // broadcast disconnection message to all clients
        controllerRef.set(null); // clear controller
        views.clear(); // free the views map
        if (heartbeatThread != null) heartbeatThread.interrupt(); // stop heartbeat thread
        if (persistence != null) persistence.delete(); // delete save — game ended due to disconnection
    }
    // returns colours not yet claimed by other players (SYNCHRONIZED)
    public synchronized List<TotemColor> getAvailableColors() {
        Set<TotemColor> taken = new HashSet<>(chosenColors.values());
        return Arrays.stream(TotemColor.values())
                     .filter(c -> !taken.contains(c)) // only colours not yet taken
                     .collect(Collectors.toList()); // return as list
    }
    // assigns a colour to a nickname (SYNCHRONIZED)
    public synchronized TotemColor claimColor(String nick, TotemColor requested) {
        List<TotemColor> available = getAvailableColors();
        TotemColor assigned = (requested != null && available.contains(requested)) // check if requested colour is available
                ? requested : available.get(0);
        chosenColors.put(nick, assigned); // store in the map
        return assigned; // return the colour assigned to this nickname
    }

    public TotemColor getChosenColor(String nick) { return chosenColors.get(nick); } // returns the colour assigned to the nick

    public void unregister(String nick) {
        views.remove(nick);
    }

    public VirtualView get(String nick) { return views.get(nick); }

    public List<String> getNicks() { return new ArrayList<>(views.keySet()); }

    public int getRegisteredCount() { return views.size(); }
    // true when the expected number of players has connected
    public boolean isFull() {
        int exp = expectedPlayers.get();
        return exp > 0 && views.size() >= exp;
    }
    // set by StartMessage
    public void setController(Controller c) { controllerRef.set(c); }

    public Controller getController() { return controllerRef.get(); }
    public void setRepository(ScoreRepository repo) { this.repository = repo; }

    public ScoreRepository getRepository() { return repository; }

    // setter for game persistence: called by ServerMain
    public void setPersistence(GamePersistence p) { this.persistence = p; }
    // getter for game persistence
    public GamePersistence getPersistence() { return persistence; }
    // setter for saved game: called by ServerMain
    public void setSavedGame(Game g) {
        this.savedGame = g;
        expectedPlayers.set(g.getPlayers().size()); // set expected player count from saved game
        startRecoveryTimeout(60); // start recovery timer
    }
    // getter for saved game
    public Game getSavedGame() { return savedGame; }
    // true if a recovery is in progress (saved game present)
    public boolean isRecovering() { return savedGame != null; }
    // called by TcpClientHandler or RemoteGameServiceImpl at the end of handleRecovery()
    public synchronized void checkRecoveryComplete() {
        if (!isRecovering()) return; // not in recovery
        if (getRegisteredCount() < getExpectedPlayers()) return; // not enough players yet
        // all players reconnected — resume game
        Game game = savedGame;
        Controller ctrl = new Controller(game); // create controller
        ctrl.setListener(new ModelListener(evt -> { // wire listener to controller
            broadcast(SnapshotMessage.from(game));
            if (persistence != null && game.getPhase() == GamePhase.PLACEMENT)
                persistence.save(game); // save game on every PLACEMENT event
            if (game.getPhase() == GamePhase.GAME_OVER) { // game finished
                if (persistence != null) persistence.delete(); // delete persistence
                ScoreRepository repo = getRepository(); // show DB leaderboard
                if (repo != null) {
                    try {
                        repo.saveResults(game.getRanking(), game.getPlayers().size()); // save results
                        broadcast(new RankingMessage(repo.getRanking(game.getPlayers().size()))); // send ranking to all clients
                    } catch (SQLException e) { // failed to build final leaderboard
                        System.err.println("[db] save failed: " + e.getMessage());
                    }
                }
            }
        }));
        setController(ctrl); // register controller so ControllerWorker can access it
        savedGame = null;
        broadcast(SnapshotMessage.from(game));
    }
    // recovery timeout timer: called by setSavedGame()
    private void startRecoveryTimeout(int seconds) {
        Thread t = new Thread(() -> { // timeout thread
            try { Thread.sleep(seconds * 1000L); } // sleep 60s = 1 min
                 catch (InterruptedException e) { return; }
            if (!isRecovering()) return; // check whether recovery is still ongoing
            // NOTE: checkRecoveryComplete(), if it completed, should have nulled out savedGame
            System.out.println("[recovery] timeout — not all players reconnected, shutting down");
            if (persistence != null) persistence.delete(); // delete save — game lost
            System.exit(1); // bring down the server → clients lose their TCP connection
        }, "recovery-timeout");
        t.setDaemon(true); // daemon thread
        t.start();
    }

    // sends to each VirtualView which forwards the message over its stream to the client
    public void broadcast(ServerMessage msg) {
        views.values().forEach(v -> v.sendMessage(msg));
    }
    // sleeps 2 seconds then broadcasts a HeartbeatMessage; repeats until interrupted
    public void startHeartbeat() {
        heartbeatThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) { // run until interrupted
                try { Thread.sleep(2_000); } catch (InterruptedException e) { return; }
                broadcast(new HeartbeatMessage()); // broadcast heartbeat to all clients
            }
        }, "heartbeat");
        heartbeatThread.setDaemon(true); // daemon thread
        heartbeatThread.start();
    }
}
