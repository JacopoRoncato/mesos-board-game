package mesos.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import mesos.enums.TotemColor;
import mesos.game.Game;
import mesos.network.client.ClientMessage;
import mesos.network.client.ColorMessage;
import mesos.network.client.LoginMessage;
import mesos.network.server.ErrMessage;
import mesos.network.server.InfoMessage;
import mesos.network.server.OkMessage;
import mesos.player.Player;

/**
 * Per-client TCP handler. Runs in its own daemon thread.
 *
 * Lifecycle:
 *  1. Opens ObjectOutputStream + ObjectInputStream on the socket.
 *  2. Reads the first object — must be a LoginMessage.
 *  3. Creates TcpVirtualView for this client and registers it in the registry.
 *  4. Reads subsequent ClientMessage objects in a loop, sets sender, and
 *     enqueues them for the ControllerWorker.
 */
public class TcpClientHandler implements Runnable {

    private final Socket socket;
    private final BlockingQueue<ClientMessage> queue;
    private final ServerRegistry registry;

    /**
     * Creates a handler for the given client socket.
     *
     * @param socket   the connected client socket
     * @param queue    the shared command queue
     * @param registry the server registry
     */
    public TcpClientHandler(Socket socket,
                            BlockingQueue<ClientMessage> queue,
                            ServerRegistry registry) {
        this.socket   = socket;
        this.queue    = queue;
        this.registry = registry;
    }

    @Override
    public void run() {
        String registeredNick = null;
        try (socket) {
            // ObjectOutputStream must be created before ObjectInputStream
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // ── Login handshake ───────────────────────────────────────────────
            Object first = in.readObject();
            if (!(first instanceof LoginMessage login)) {
                System.err.println("[tcp] first message is not LoginMessage — closing");
                return;
            }

            TcpVirtualView view = new TcpVirtualView(out, login.getNick(), registry);

            registeredNick = registry.isRecovering()
                    ? handleRecovery(login.getNick(), view, in)
                    : handleLobby(login.getNick(), view, in);

            if (registeredNick == null) return;

            // ── Game message loop ─────────────────────────────────────────────
            while (true) {
                Object obj = in.readObject();
                if (!(obj instanceof ClientMessage)) continue;
                ClientMessage msg = (ClientMessage) obj;
                msg.setSender(view);
                queue.put(msg);
            }

        } catch (IOException e) {
            System.out.println("[tcp] client " + socket.getRemoteSocketAddress()
                    + " disconnected: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("[tcp] unknown class: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (registeredNick != null) registry.handleDisconnection(registeredNick);
    }

    private String handleLobby(String nick, TcpVirtualView view, ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        if (!registry.tryRegister(nick, view)) {
            view.sendMessage(new ErrMessage("NICKNAME_TAKEN " + nick));
            return null;
        }
        String registeredNick = nick;

        if (registry.getExpectedPlayers() == -1) {
            view.sendMessage(new OkMessage("LOBBY_FIRST"));
            Object second = in.readObject();
            if (!(second instanceof LoginMessage lm2)
                    || lm2.getNumPlayers() < 2
                    || lm2.getNumPlayers() > 5) {
                view.sendMessage(new ErrMessage("INVALID_PLAYER_COUNT"));
                registry.unregister(registeredNick);
                return null;
            }
            registry.setExpectedPlayers(lm2.getNumPlayers());
            view.sendMessage(new OkMessage("LOBBY_ACCEPTED"));
        } else {
            view.sendMessage(new OkMessage("LOBBY_JOINED "
                    + registry.getRegisteredCount() + "/" + registry.getExpectedPlayers()));
        }
        view.sendMessage(new InfoMessage(registeredNick,
                registry.getRegisteredCount(),
                registry.getExpectedPlayers(),
                registry.getAvailableColors()));
        Object colorObj = in.readObject();
        TotemColor requested = (colorObj instanceof ColorMessage cm) ? cm.getColor() : null;
        TotemColor assigned  = registry.claimColor(registeredNick, requested);
        view.sendMessage(new OkMessage("COLOR_ACCEPTED " + assigned));
        return registeredNick;
    }

    private String handleRecovery(String nick, TcpVirtualView view, ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        Game saved = registry.getSavedGame();
        boolean known = saved.getPlayers().stream().anyMatch(p -> p.getId().equals(nick));
        if (!known) {
            view.sendMessage(new ErrMessage("NICK_NOT_IN_SAVE"));
            return null;
        }
        if (!registry.tryRegister(nick, view)) {
            view.sendMessage(new ErrMessage("NICK_ALREADY_RECOVERING"));
            return null;
        }

        view.sendMessage(new OkMessage("RECOVERY_JOINED "
                + registry.getRegisteredCount() + "/" + registry.getExpectedPlayers()));

        TotemColor savedColor = saved.getPlayers().stream()
                .filter(p -> p.getId().equals(nick))
                .findFirst()
                .map(Player::getTotem)
                .map(t -> t.getColor())
                .orElseThrow();
        view.sendMessage(new InfoMessage(nick, registry.getRegisteredCount(),
                registry.getExpectedPlayers(), List.of(savedColor)));
        in.readObject(); // consume ColorMessage — color is forced from save
        registry.claimColor(nick, savedColor);
        view.sendMessage(new OkMessage("COLOR_ACCEPTED " + savedColor));

        registry.checkRecoveryComplete();
        return nick;
    }
}
