package mesos.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import mesos.enums.TotemColor;
import mesos.network.client.ClientMessage;
import mesos.network.client.ColorMessage;
import mesos.network.client.LoginMessage;
import mesos.network.server.ErrMessage;
import mesos.network.server.HeartbeatMessage;
import mesos.network.server.InfoMessage;
import mesos.network.server.OkMessage;
import mesos.network.server.ServerMessage;

/**
 * TCP implementation of {@link Transport}.
 *
 * <p>Opens a socket to the server, serializes outbound {@link ClientMessage}
 * objects via {@link ObjectOutputStream}, and delivers inbound
 * {@link ServerMessage} objects to the registered listener on a dedicated
 * daemon reader thread.
 *
 * <p>The {@link ObjectOutputStream} is created and flushed before the
 * {@link ObjectInputStream} to avoid the header-exchange deadlock that occurs
 * when both sides open OIS first.
 */
public class TcpTransport implements Transport {

    private ObjectOutputStream    out;
    private Socket                socket;
    private Consumer<ServerMessage> onMessage;

    @Override
    public void setOnMessage(Consumer<ServerMessage> listener) {
        this.onMessage = listener;
    }

    @Override
    public void connect(String host, int port, String nick,
                        Supplier<Integer> askNumPlayers,
                        Function<List<TotemColor>, TotemColor> askColor) throws Exception {
        socket = new Socket(host, port);

        // OOS must be created and flushed before OIS to avoid deadlock
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

        // Step 1: send login without numPlayers — server replies LOBBY_FIRST or LOBBY_JOINED
        out.writeObject(new LoginMessage(nick, 0));
        out.flush();

        // Step 2: read lobby response synchronously (reader thread not started yet);
        // heartbeat messages are skipped to avoid breaking the expected handshake sequence
        Object lobbyResp = readHandshake(in);
        if (lobbyResp instanceof ErrMessage err) {
            socket.close();
            System.err.println("[tcp] login failed: " + err.getReason());
            throw new Exception(err.getReason());
        }
        if (lobbyResp instanceof OkMessage ok) {
            if (ok.getText().startsWith("RECOVERY_JOINED") && onMessage != null) {
                onMessage.accept(ok);
            } else if (ok.getText().equals("LOBBY_FIRST")) {
                int n = askNumPlayers.get();
                out.writeObject(new LoginMessage(nick, n));
                out.flush();

                Object confirm = readHandshake(in);
                if (confirm instanceof ErrMessage err) {
                    socket.close();
                    System.err.println("[tcp] lobby rejected: " + err.getReason());
                    throw new Exception(err.getReason());
                }
            }
        }

        // Step 4: read InfoMessage with available colors and choose a color (synchronous)
        Object infoObj = readHandshake(in);
        if (infoObj instanceof InfoMessage info && onMessage != null) {
            onMessage.accept(info);
        }
        List<TotemColor> available = (infoObj instanceof InfoMessage info2)
                ? info2.getAvailableColors() : List.of();
        TotemColor chosen = askColor.apply(available);
        out.writeObject(new ColorMessage(chosen));
        out.flush();
        Object colorAck = readHandshake(in);
        if (colorAck instanceof OkMessage ack && onMessage != null) {
            onMessage.accept(ack);
        }

        Thread reader = new Thread(() -> {
            try {
                while (true) {
                    Object obj = in.readObject();
                    if (obj instanceof ServerMessage msg && onMessage != null)
                        onMessage.accept(msg);
                }
            } catch (IOException | ClassNotFoundException e) {
                if (onMessage != null)
                    onMessage.accept(new ErrMessage("PLAYER_DISCONNECTED (connection lost)"));
            }
        }, "tcp-reader");
        reader.setDaemon(true);
        reader.start();
    }

    @Override
    public void send(ClientMessage msg) throws Exception {
        synchronized (out) {
            out.writeObject(msg);
            out.flush();
        }
    }

    @Override
    public void close() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    /**
     * Reads the next non-heartbeat message from the stream during the synchronous handshake.
     * Skips any interleaved {@link HeartbeatMessage}s that arrive before the expected response.
     */
    private Object readHandshake(ObjectInputStream in) throws IOException, ClassNotFoundException {
        Object obj;
        do { obj = in.readObject(); } while (obj instanceof HeartbeatMessage);
        return obj;
    }
}
