package mesos.client;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import mesos.enums.TotemColor;
import mesos.network.client.ClientMessage;
import mesos.network.server.ErrMessage;
import mesos.network.server.InfoMessage;
import mesos.network.server.OkMessage;
import mesos.network.server.ServerMessage;
import mesos.server.RemoteGameService;

/**
 * RMI implementation of {@link Transport}.
 *
 * <p>This class extends {@link UnicastRemoteObject} so that the server can call
 * {@link #onServerMessage} directly as a remote method. Without exporting the
 * object via {@code super()}, the server receives a {@link RemoteException} on
 * the first push attempt.
 *
 * <p>Outbound messages are sent via {@link RemoteGameService#sendMessage}.
 * Inbound messages arrive on a JVM-managed RMI thread and are forwarded to the
 * listener registered with {@link #setOnMessage}.
 */
public class RmiTransport extends UnicastRemoteObject implements Transport, RemoteGameCallback {

    private static final long serialVersionUID = 1L;

    private RemoteGameService        service;
    private String                   nick;
    private Consumer<ServerMessage>  onMessage;

    /**
     * Creates and exports this object so the server can call back into it.
     *
     * @throws RemoteException if the export fails
     */
    public RmiTransport() throws RemoteException {
        super(1235); // exports this object — required for server push to work
    }

    @Override
    public void setOnMessage(Consumer<ServerMessage> listener) {
        this.onMessage = listener;
    }

    /**
     * Looks up the {@link RemoteGameService} in the RMI registry and calls
     * {@code login}, passing {@code this} as the callback stub.
     */
    @Override
    public void connect(String host, int port, String nick,
                        Supplier<Integer> askNumPlayers,
                        Function<List<TotemColor>, TotemColor> askColor) throws Exception {
        this.nick = nick;
        service = (RemoteGameService)
                LocateRegistry.getRegistry(host, port).lookup("mesos-game");
        String status;
        try {
            status = service.login(nick, this);
        } catch (RemoteException e) {
            System.err.println("[rmi] login failed: " + e.getMessage());
            throw new Exception(e.getMessage());
        }

        int playerIndex;
        int totalPlayers;
        if (status.startsWith("RECOVERY_JOINED") && onMessage != null) {
            onMessage.accept(new OkMessage(status));
        }

        if (status.equals("LOBBY_FIRST")) {
            int n = askNumPlayers.get();
            try {
                service.setNumPlayers(n);
            } catch (RemoteException e) {
                System.err.println("[rmi] lobby rejected: " + e.getMessage());
                throw new Exception(e.getMessage());
            }
            playerIndex  = 1;
            totalPlayers = n;
        } else {
            // "LOBBY_JOINED n/m" or "RECOVERY_JOINED n/m" → split on space and slash
            String[] parts = status.split("[ /]");
            playerIndex  = Integer.parseInt(parts[1]);
            totalPlayers = Integer.parseInt(parts[2]);
        }

        // In recovery: returns only the saved color for this nick; otherwise all available
        List<TotemColor> available = service.getAvailableColorsForNick(nick);
        if (onMessage != null)
            onMessage.accept(new InfoMessage(nick, playerIndex, totalPlayers, available));
        TotemColor chosen   = askColor.apply(available);
        TotemColor assigned = service.claimColor(nick, chosen);
        if (onMessage != null)
            onMessage.accept(new OkMessage("COLOR_ACCEPTED " + assigned));
    }

    /**
     * Called remotely by the server on every game state change.
     * Forwards the message to the registered listener (e.g. TuiClient or GuiClient).
     */
    @Override
    public void onServerMessage(ServerMessage message) throws RemoteException {
        if (onMessage != null) onMessage.accept(message);
    }

    @Override
    public void send(ClientMessage msg) throws Exception {
        try {
            service.sendMessage(msg, nick);
        } catch (RemoteException e) {
            if (onMessage != null)
                onMessage.accept(new ErrMessage("PLAYER_DISCONNECTED (connection lost)"));
        }
    }

    @Override
    public void close() {
        try { UnicastRemoteObject.unexportObject(this, true); } catch (Exception ignored) {}
    }
}
