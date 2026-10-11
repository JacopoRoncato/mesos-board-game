package mesos.network.client;

import mesos.controller.Controller;

/**
 * First message sent by any client after connecting.
 * Carries the player's nickname and, for the first client only, the total
 * number of expected players (0 means "not the first client").
 *
 * LoginMessage is handled directly by TcpClientHandler / RemoteGameServiceImpl
 * at the transport layer and is NOT put into the shared queue.
 */
public class LoginMessage extends ClientMessage {
    private static final long serialVersionUID = 1L;

    private final String nick;
    private final int numPlayers;

    /**
     * Creates a login message.
     *
     * @param nick       the player's chosen nickname
     * @param numPlayers the total number of expected players, or 0 if this client is not the first
     */
    public LoginMessage(String nick, int numPlayers) {
        this.nick       = nick;
        this.numPlayers = numPlayers;
    }

    /** Returns the player's nickname. */
    public String getNick()       { return nick; }

    /** Returns the expected player count supplied by the first client, or 0 for subsequent clients. */
    public int    getNumPlayers() { return numPlayers; }

    @Override
    public void process(Controller controller) {
        // Handled at transport layer — never called via the queue.
    }
}
