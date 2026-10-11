package mesos.network.client;

import java.io.Serializable;
import mesos.controller.Controller;
import mesos.server.ServerRegistry;
import mesos.server.VirtualView;

/**
 * Base class for all messages sent from client to server.
 *
 * Transport layer (TcpClientHandler / RemoteGameServiceImpl) sets sender and
 * registry before calling process(). The ControllerWorker then calls process()
 * so that the message dispatches itself to the correct Controller method
 * (visitor / double-dispatch pattern).
 */
public abstract class ClientMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    protected transient VirtualView    sender;
    protected transient ServerRegistry registry;

    public void setSender(VirtualView v)       { this.sender   = v; }
    public void setRegistry(ServerRegistry r)  { this.registry = r; }

    public VirtualView    getSender()   { return sender; }
    public ServerRegistry getRegistry() { return registry; }

    /**
     * Double-dispatch entry point called by ControllerWorker.
     * Each subclass calls the appropriate method on the controller.
     * {@code controller} may be null if StartMessage has not yet been processed.
     */
    public abstract void process(Controller controller);
}