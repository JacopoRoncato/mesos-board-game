package mesos.server;

import java.io.IOException;
import java.io.ObjectOutputStream;
import mesos.network.server.ServerMessage;

/**
 * TCP implementation of {@link VirtualView}.
 * Serializes {@link ServerMessage} objects and writes them to the client socket.
 * Thread-safe: synchronizes on the output stream so concurrent broadcast and
 * login-phase writes cannot corrupt the stream.
 */
public class TcpVirtualView implements VirtualView {
    private final ObjectOutputStream out;
    private final String nick;
    private final ServerRegistry registry;

    /**
     * Creates a TCP virtual view for the given client.
     *
     * @param out      the output stream connected to the client socket
     * @param nick     the player's nickname (used for disconnection handling)
     * @param registry the server registry (needed to call handleDisconnection on write failure)
     */
    public TcpVirtualView(ObjectOutputStream out, String nick, ServerRegistry registry) {
        this.out      = out;
        this.nick     = nick;
        this.registry = registry;
    }

    @Override
    public void sendMessage(ServerMessage message) {
        // synchronized on out: both TcpClientHandler (login) and ControllerWorker (broadcast)
        // write to the same ObjectOutputStream — without the lock the stream can corrupt.
        synchronized (out) {
            try {
                out.writeObject(message);
                out.flush();
            } catch (IOException e) {
                System.err.println("[tcp] send error to " + nick + ": " + e.getMessage());
                registry.handleDisconnection(nick);
            }
        }
    }

    @Override
    public String getNick() { return nick; }
}
