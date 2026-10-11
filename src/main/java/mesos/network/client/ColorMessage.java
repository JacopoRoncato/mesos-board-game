package mesos.network.client;

import mesos.controller.Controller;
import mesos.enums.TotemColor;

/**
 * Sent by the client during the lobby handshake to claim a totem color.
 * Handled at transport layer — never enqueued in the ControllerWorker queue.
 */
public class ColorMessage extends ClientMessage {
    private static final long serialVersionUID = 1L;

    private final TotemColor color;

    /**
     * Creates a colour message.
     *
     * @param color the totem colour the player requests; may be {@code null} for auto-assign
     */
    public ColorMessage(TotemColor color) { this.color = color; }

    /** Returns the totem colour requested by the player. */
    public TotemColor getColor() { return color; }

    @Override
    public void process(Controller controller) {
        // handled at transport layer — never called via queue
    }
}
