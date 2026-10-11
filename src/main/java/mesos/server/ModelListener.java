package mesos.server;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Bridges {@link PropertyChangeListener} (required by the Model API) to the
 * {@link CallBack} functional interface used by the server infrastructure.
 * Registered on the Model via {@link mesos.game.Game#setListener}; forwards
 * every event to the callback lambda supplied at construction time.
 */
public class ModelListener implements PropertyChangeListener {

    private final CallBack callBack;

    /**
     * Creates a model listener that forwards events to the given callback.
     *
     * @param callBack the callback to invoke on every model change
     */
    public ModelListener(CallBack callBack) {
        this.callBack = callBack;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        callBack.gotEvent(evt);
    }
}
