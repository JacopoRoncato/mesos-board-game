package mesos.network.server;

/**
 * Keep-alive message sent by the server every few seconds.
 * Clients use it to detect a dead server: if no message (including heartbeat)
 * arrives within the watchdog timeout, the client exits.
 */
public class HeartbeatMessage implements ServerMessage {
    private static final long serialVersionUID = 1L;
}
