package mesos.network.client;

import mesos.controller.Controller;
import mesos.network.server.ErrMessage;
import mesos.network.server.OkMessage;

/**
 * Generic client-to-server message that wraps a raw command string.
 * Dispatches to Controller.processCmd() and sends back OkMessage / ErrMessage.
 */
public class CommandMessage extends ClientMessage {
    private static final long serialVersionUID = 1L;

    private final String cmd;

    /**
     * Creates a command message.
     *
     * @param cmd the raw command string (e.g. {@code "PLACE alice B"})
     */
    public CommandMessage(String cmd) { this.cmd = cmd; }

    @Override
    public void process(Controller controller) {
        if (controller == null) {
            sender.sendMessage(new ErrMessage("GAME_NOT_STARTED"));
            return;
        }
        String reply = controller.processCmd(cmd);
        if (reply.startsWith("OK "))
            sender.sendMessage(new OkMessage(reply.substring(3)));
        else if (reply.startsWith("ERR "))
            sender.sendMessage(new ErrMessage(reply.substring(4)));
        else
            sender.sendMessage(new OkMessage(reply));
    }
}
