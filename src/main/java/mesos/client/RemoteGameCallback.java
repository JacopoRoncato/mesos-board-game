package mesos.client;

import java.rmi.Remote;
import java.rmi.RemoteException;
import mesos.network.server.ServerMessage;

/**
 * RMI callback interface implemented by the client.
 * The server calls {@link #onServerMessage} to push updates back to the RMI client.
 */
public interface RemoteGameCallback extends Remote {
    /**
     * Called by the server whenever a new {@link ServerMessage} is available for this client.
     *
     * @param message the message to deliver
     * @throws RemoteException if the remote call fails
     */
    void onServerMessage(ServerMessage message) throws RemoteException;
}
