package mesos.server;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import mesos.client.RemoteGameCallback;
import mesos.enums.TotemColor;
import mesos.network.client.ClientMessage;

/** RMI remote interface implemented by {@link RemoteGameServiceImpl}. */
public interface RemoteGameService extends Remote {
    /** Registers the client. Returns "LOBBY_FIRST" if this client must set the player count,
     *  or "LOBBY_JOINED n/m" otherwise. Throws RemoteException on NICKNAME_TAKEN. */
    String login(String nick, RemoteGameCallback callback) throws RemoteException;
    /** Sets the expected player count. Called only by the first client after LOBBY_FIRST.
     *  Throws RemoteException if n is not in [2, 5]. */
    void setNumPlayers(int n) throws RemoteException;
    /** Returns the list of totem colors not yet claimed by any player. */
    List<TotemColor> getAvailableColors() throws RemoteException;
    /** In recovery mode returns only the saved color for nick; otherwise same as getAvailableColors(). */
    List<TotemColor> getAvailableColorsForNick(String nick) throws RemoteException;
    /** Claims a color for the given nick; returns the actually assigned color. */
    TotemColor claimColor(String nick, TotemColor requested) throws RemoteException;
    /** Enqueues a client message on behalf of the given nick. */
    void sendMessage(ClientMessage msg, String nick) throws RemoteException;
}
