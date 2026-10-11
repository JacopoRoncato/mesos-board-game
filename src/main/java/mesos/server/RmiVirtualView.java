package mesos.server;

import java.rmi.RemoteException;
import mesos.client.RemoteGameCallback;
import mesos.network.server.ServerMessage;

/**
 * RMI implementation of {@link VirtualView}.
 * Calls the client-side {@link RemoteGameCallback} stub to push messages back.
 * On {@link RemoteException}, triggers disconnection handling so the registry is cleaned up.
 */
public class RmiVirtualView implements VirtualView {

    private final RemoteGameCallback callback;
    private final String             nick;
    private final ServerRegistry     registry;

    /**
     * Creates an RMI virtual view.
     *
     * @param callback the client-side callback stub
     * @param nick     the player's nickname
     * @param registry the server registry (needed for disconnection handling)
     */
    public RmiVirtualView(RemoteGameCallback callback, String nick, ServerRegistry registry) {
        this.callback = callback;
        this.nick     = nick;
        this.registry = registry;
    }

    @Override
    public void sendMessage(ServerMessage message) {
        try {
            callback.onServerMessage(message);
        } catch (RemoteException e) {
            System.err.println("[rmi] client " + nick + " disconnected: " + e.getMessage());
            registry.handleDisconnection(nick);
        }
    }

    @Override
    public String getNick() { return nick; }
}
