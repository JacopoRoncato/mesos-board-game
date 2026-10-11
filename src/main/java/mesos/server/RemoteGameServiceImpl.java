package mesos.server;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import mesos.client.RemoteGameCallback;
import mesos.enums.TotemColor;
import mesos.network.client.ClientMessage;
import mesos.player.Player;

/**
 * RMI remote object: the server-side access point for RMI clients.
 * Bound in the RMI registry under the name {@code "mesos-game"} by {@link ServerMain}.
 */
public class RemoteGameServiceImpl extends UnicastRemoteObject implements RemoteGameService {

    private static final long serialVersionUID = 1L;

    private final BlockingQueue<ClientMessage> queue;
    private final ServerRegistry registry;

    /**
     * Creates the RMI service implementation.
     *
     * @param queue    the shared message queue fed to the {@link ControllerWorker}
     * @param registry the server registry
     * @throws RemoteException if the RMI runtime encounters an error during export
     */
    public RemoteGameServiceImpl(BlockingQueue<ClientMessage> queue,
                                 ServerRegistry registry) throws RemoteException {
        super();
        this.queue    = queue;
        this.registry = registry;
    }

    @Override
    public String login(String nick, RemoteGameCallback callback) throws RemoteException {
        RmiVirtualView view = new RmiVirtualView(callback, nick, registry);
        if (registry.isRecovering()) {
            boolean known = registry.getSavedGame().getPlayers().stream()
                    .anyMatch(p -> p.getId().equals(nick));
            if (!known) throw new RemoteException("NICK_NOT_IN_SAVE");
            if (!registry.tryRegister(nick, view)) throw new RemoteException("NICK_ALREADY_RECOVERING");
            return "RECOVERY_JOINED " + registry.getRegisteredCount() + "/" + registry.getExpectedPlayers();
        }
        if (!registry.tryRegister(nick, view))
            throw new RemoteException("NICKNAME_TAKEN " + nick);
        if (registry.getExpectedPlayers() == -1)
            return "LOBBY_FIRST";
        return "LOBBY_JOINED " + registry.getRegisteredCount() + "/" + registry.getExpectedPlayers();
    }

    @Override
    public void setNumPlayers(int n) throws RemoteException {
        if (n < 2 || n > 5)
            throw new RemoteException("INVALID_PLAYER_COUNT: " + n);
        registry.setExpectedPlayers(n);
    }

    @Override
    public List<TotemColor> getAvailableColors() throws RemoteException {
        return registry.getAvailableColors();
    }

    @Override
    public List<TotemColor> getAvailableColorsForNick(String nick) throws RemoteException {
        if (registry.isRecovering()) {
            return registry.getSavedGame().getPlayers().stream()
                    .filter(p -> p.getId().equals(nick))
                    .map(p -> p.getTotem().getColor())
                    .collect(java.util.stream.Collectors.toList());
        }
        return registry.getAvailableColors();
    }

    @Override
    public TotemColor claimColor(String nick, TotemColor requested) throws RemoteException {
        TotemColor assigned = registry.claimColor(nick, requested);
        registry.checkRecoveryComplete();
        return assigned;
    }

    @Override
    public void sendMessage(ClientMessage msg, String nick) throws RemoteException {
        VirtualView view = registry.get(nick);
        if (view == null) throw new RemoteException("nick not registered: " + nick);
        msg.setSender(view);
        try {
            queue.put(msg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteException("interrupted while enqueuing message");
        }
    }
}
