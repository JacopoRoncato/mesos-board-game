package mesos.client;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import mesos.enums.TotemColor;
import mesos.network.client.ClientMessage;
import mesos.network.server.ServerMessage;

/**
 * Abstracts the network transport layer from the view (TUI / GUI).
 *
 * <p>Implementations handle the connection lifecycle and message exchange for a
 * specific technology (TCP object serialization or RMI). The view only calls
 * {@link #connect}, {@link #send}, and {@link #setOnMessage} — it has no
 * knowledge of sockets, streams, or RMI stubs.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Call {@link #setOnMessage} to register the inbound message handler.</li>
 *   <li>Call {@link #connect} to open the connection and send the login.</li>
 *   <li>Call {@link #send} for each outbound {@link ClientMessage}.</li>
 *   <li>Call {@link #close} when the session ends.</li>
 * </ol>
 *
 * @see TcpTransport
 * @see RmiTransport
 */
public interface Transport {

    /**
     * Opens the connection to the server and performs the lobby handshake.
     *
     * <p>The transport sends the initial login, reads the server response, and
     * invokes {@code askNumPlayers} only if the server replies {@code LOBBY_FIRST}
     * (i.e. this is the first connecting client). Subsequent clients receive
     * {@code LOBBY_JOINED} and the supplier is never called.
     *
     * @param host           server hostname or IP address
     * @param port           server port (TCP: 1234 default, RMI: 1099 default)
     * @param nick           player nickname
     * @param askNumPlayers  called only when this client must set the player count;
     *                       must return a value in [2, 5]
     * @param askColor       called after lobby join with the list of available colors;
     *                       must return the chosen {@link TotemColor} (or {@code null} for auto-assign)
     * @throws Exception if the connection or login fails (e.g. NICKNAME_TAKEN)
     */
    void connect(String host, int port, String nick,
                 Supplier<Integer> askNumPlayers,
                 Function<List<TotemColor>, TotemColor> askColor) throws Exception;

    /**
     * Sends a {@link ClientMessage} to the server.
     *
     * @param msg the message to send
     * @throws Exception if the send fails
     */
    void send(ClientMessage msg) throws Exception;

    /**
     * Registers the handler invoked for every {@link ServerMessage} received
     * from the server. Must be called before {@link #connect}.
     *
     * @param listener called on the transport's reader thread (TCP) or on the
     *                 RMI callback thread — implementations must be thread-safe
     *                 with respect to the view's rendering logic
     */
    void setOnMessage(Consumer<ServerMessage> listener);

    /**
     * Closes the connection and releases any resources.
     * Safe to call more than once.
     */
    void close();
}
