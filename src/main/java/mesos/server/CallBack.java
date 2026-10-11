package mesos.server;

import java.beans.PropertyChangeEvent;

/**
 * Functional interface that receives a {@link PropertyChangeEvent} from the Model.
 * Implemented as a lambda in {@link mesos.network.client.StartMessage} to broadcast
 * a {@link mesos.network.server.SnapshotMessage} to all connected clients on every change.
 */
@FunctionalInterface
public interface CallBack {
    /** Called when the model fires a property change event. */
    void gotEvent(PropertyChangeEvent evt);
}
