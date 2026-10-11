package mesos.network.server;

/** Positive acknowledgement sent from server to client. Also used for lobby status strings. */
public class OkMessage implements ServerMessage {
    private static final long serialVersionUID = 1L;
    private final String text;

    /**
     * Creates an OK message.
     *
     * @param text the payload text (e.g. {@code "LOBBY_FIRST"} or {@code "COLOR_ACCEPTED RED"})
     */
    public OkMessage(String text) { this.text = text; }

    /** Returns the payload text of this message. */
    public String getText()       { return text; }

    @Override public String toString() { return "OK " + text; }
}
