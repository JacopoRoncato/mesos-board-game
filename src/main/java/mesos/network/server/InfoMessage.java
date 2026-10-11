package mesos.network.server;

import java.util.List;
import mesos.enums.TotemColor;

/**
 * Sent by the server to a client immediately after the lobby handshake.
 * Carries the client's nick, join-order index, total expected players, and
 * the list of totem colors still available for selection.
 */
public class InfoMessage implements ServerMessage {
    private static final long serialVersionUID = 2L;

    private final String           nick;
    private final int              playerIndex;
    private final int              totalPlayers;
    private final List<TotemColor> availableColors;

    /**
     * Creates an info message.
     *
     * @param nick            the player's nickname
     * @param playerIndex     the 1-based join index of this player
     * @param totalPlayers    the total number of expected players
     * @param availableColors the totem colors still available for selection
     */
    public InfoMessage(String nick, int playerIndex, int totalPlayers, List<TotemColor> availableColors) {
        this.nick            = nick;
        this.playerIndex     = playerIndex;
        this.totalPlayers    = totalPlayers;
        this.availableColors = availableColors;
    }

    /** Returns the player's nickname. */
    public String           getNick()            { return nick; }
    /** Returns the 1-based join index of this player. */
    public int              getPlayerIndex()      { return playerIndex; }
    /** Returns the total number of expected players. */
    public int              getTotalPlayers()     { return totalPlayers; }
    /** Returns the list of totem colors available for selection. */
    public List<TotemColor> getAvailableColors()  { return availableColors; }

    @Override
    public String toString() {
        return "INFO you=" + nick + " (" + playerIndex + "/" + totalPlayers + ")";
    }
}
