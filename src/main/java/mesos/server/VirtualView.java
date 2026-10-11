package mesos.server;

import mesos.network.server.ServerMessage;

/**
 * Outbound channel from the server to one specific client.
 * The Model/Controller uses this to push {@link ServerMessage} objects back to the client,
 * regardless of whether the transport is TCP or RMI.
 */
public interface VirtualView {
    /**
     * Sends a message to this client.
     *
     * @param message the message to deliver
     */
    void sendMessage(ServerMessage message);

    /** Returns the nickname of the player associated with this view. */
    String getNick();
}
