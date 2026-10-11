package mesos.network.server;

/** Error message sent from server to client when a command fails or an illegal action is detected. */
public class ErrMessage implements ServerMessage {
    private static final long serialVersionUID = 1L;
    private final String reason;

    /**
     * Creates an error message.
     *
     * @param reason a short human-readable description of the error
     */
    public ErrMessage(String reason) { this.reason = reason; }

    /** Returns the error reason string. */
    public String getReason()        { return reason; }

    @Override public String toString() { return "ERR " + reason; }
}
