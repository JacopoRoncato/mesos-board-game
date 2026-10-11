package mesos.server;

import java.util.concurrent.BlockingQueue;
import mesos.network.client.ClientMessage;

/**
 * Single-threaded consumer that drains the shared ClientMessage queue.
 *
 * For each message:
 *  1. Injects the ServerRegistry (option C from the design).
 *  2. Calls message.process(controller) where controller comes from the
 *     registry (null before StartMessage has run).
 *
 * Running on a single thread keeps Controller access naturally serialized.
 */
public class ControllerWorker implements Runnable {

    private final BlockingQueue<ClientMessage> queue;
    private final ServerRegistry registry;

    /**
     * Creates a controller worker.
     *
     * @param queue    the shared message queue fed by TCP/RMI handlers
     * @param registry the server registry used to retrieve the current controller
     */
    public ControllerWorker(BlockingQueue<ClientMessage> queue, ServerRegistry registry) {
        this.queue    = queue;
        this.registry = registry;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                ClientMessage msg = queue.take();
                msg.setRegistry(registry);
                msg.process(registry.getController());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
