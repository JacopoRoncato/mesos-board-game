package mesos.network.client;

import java.util.ArrayList;
import java.util.List;
import java.sql.SQLException;
import mesos.controller.Controller;
import mesos.db.ScoreRepository;
import mesos.enums.GamePhase;
import mesos.enums.PlayerCount;
import mesos.enums.TotemColor;
import mesos.game.Game;
import mesos.network.server.ErrMessage;
import mesos.network.server.OkMessage;
import mesos.network.server.RankingMessage;
import mesos.network.server.SnapshotMessage;
import mesos.player.Player;
import mesos.player.Totem;
import mesos.server.GamePersistence;
import mesos.server.ModelListener;
import mesos.server.ServerRegistry;

/**
 * Command sent by a client to start the game.
 *
 * process() is the only ClientMessage method fully implemented in this phase.
 * It:
 *  1. Checks that all expected players have logged in (registry.isFull()).
 *  2. Builds Game + Controller from the registered nicks (Flow A).
 *  3. Wires ModelListener so Model events are broadcast to all VirtualViews.
 *  4. Registers the new Controller in the registry (unblocks ControllerWorker).
 *  5. Calls processCmd("START") and sends the response back to the sender.
 */
public class StartMessage extends ClientMessage {
    private static final long serialVersionUID = 1L;

    @Override
    public void process(Controller controller) {
        ServerRegistry reg = registry;

        if (controller != null) {
            sender.sendMessage(new ErrMessage("GAME_ALREADY_STARTED"));
            return;
        }
        if (!reg.isFull()) {
            sender.sendMessage(new ErrMessage("NOT_ENOUGH_PLAYERS "
                    + reg.getRegisteredCount() + "/" + reg.getExpectedPlayers()));
            return;
        }

        // Build players using the colors chosen during lobby handshake
        List<String> nicks   = reg.getNicks();
        List<Player> players = new ArrayList<>();
        for (String n : nicks) {
            TotemColor color = reg.getChosenColor(n);
            players.add(new Player(n, new Totem(color)));
        }
        Game       game    = new Game(PlayerCount.from(nicks.size()), players);
        Controller newCtrl = new Controller(game);

        // Start WITHOUT listener: setup events are silently dropped.
        // This prevents snapshot spam — we send exactly one snapshot at the end.
        newCtrl.processCmd("START");

        // Wire listener NOW so future commands trigger snapshots.
        newCtrl.setListener(new ModelListener(evt -> {
            reg.broadcast(SnapshotMessage.from(game));
            GamePersistence persistence = reg.getPersistence();
            if (persistence != null && game.getPhase() == GamePhase.PLACEMENT)
                persistence.save(game);
            if (game.getPhase() == GamePhase.GAME_OVER) {
                if (persistence != null) persistence.delete();
                ScoreRepository repo = reg.getRepository();
                if (repo != null) {
                    try {
                        repo.saveResults(game.getRanking(), game.getPlayers().size());
                        reg.broadcast(new RankingMessage(repo.getRanking(game.getPlayers().size())));
                    } catch (SQLException e) {
                        System.err.println("[db] save failed: " + e.getMessage());
                    }
                }
            }
        }));

        // Publish controller so ControllerWorker can use it for subsequent messages.
        reg.setController(newCtrl);

        // One clean snapshot + reply to sender.
        reg.broadcast(SnapshotMessage.from(game));
        sender.sendMessage(new OkMessage("GAME_STARTED players=" + String.join(",", nicks)));
    }
}
