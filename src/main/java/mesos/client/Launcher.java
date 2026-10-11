package mesos.client;

import java.util.Scanner;
import mesos.client.tui.TuiClient;

/**
 * Unified entry point for the MESOS client.
 *
 * <p>Asks the player to select the view (TUI / GUI) and transport (TCP / RMI)
 * interactively at startup. Host and port can be passed as positional arguments;
 * if omitted, defaults to {@code 127.0.0.1} and the transport-specific port
 * (TCP: 1234, RMI: 1099).
 *
 * <p>Usage:
 * <pre>
 *   java mesos.client.Launcher [host] [port]
 * </pre>
 */
public class Launcher {

    private static final int DEFAULT_TCP_PORT = 1234;
    private static final int DEFAULT_RMI_PORT = 1099;

    /**
     * Prompts the player to select the view and transport, then starts the appropriate client.
     *
     * @param args optional: host, port, and (for RMI) client IP override
     */
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        boolean useGui = askGui(sc);
        boolean useRmi = askRmi(sc);

        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int    port = args.length > 1 ? Integer.parseInt(args[1])
                                      : (useRmi ? DEFAULT_RMI_PORT : DEFAULT_TCP_PORT);
        if (useRmi) {
            String clientIp;
            if (args.length > 2) {
                clientIp = args[2];
            } else {
                // auto-detect: socket probe asks the OS which interface it would use
                // to reach the server — this is always the correct one
                clientIp = "127.0.0.1";
                try (java.net.Socket probe = new java.net.Socket(host, port)) {
                    clientIp = probe.getLocalAddress().getHostAddress();
                } catch (java.io.IOException ignored) {}
            }
            System.setProperty("java.rmi.server.hostname", clientIp);
        }
        Transport transport = useRmi ? new RmiTransport() : new TcpTransport();

        if (useGui) {
            mesos.client.gui.GuiClient.start(transport, host, port);
        } else {
            TuiClient.start(transport, host, port);
        }
    }

    private static boolean askGui(Scanner sc) {
        System.out.println("Select interface:");
        System.out.println("  1) TUI  (text)");
        System.out.println("  2) GUI  (Swing)");
        System.out.print("> ");
        String s = sc.nextLine().trim();
        return s.equals("2") || s.equalsIgnoreCase("gui");
    }

    private static boolean askRmi(Scanner sc) {
        System.out.println("Select transport:");
        System.out.println("  1) TCP  (socket)");
        System.out.println("  2) RMI");
        System.out.print("> ");
        String s = sc.nextLine().trim();
        return s.equals("2") || s.equalsIgnoreCase("rmi");
    }
}
