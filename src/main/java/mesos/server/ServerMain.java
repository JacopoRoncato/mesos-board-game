package mesos.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.rmi.registry.LocateRegistry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import mesos.db.ScoreRepository;
import mesos.network.client.ClientMessage;

public class ServerMain {

    private static final int    DEFAULT_TCP_PORT = 1234;
    private static final int    DEFAULT_RMI_PORT = 1099;
    private static final String RMI_BINDING      = "mesos-game";

    public static void main(String[] args) throws Exception {
        // RMI hostname: remote clients use this to reach the server stub.
        // Auto-detect the LAN IP via a UDP routing lookup (no packet is actually sent).
        String rmiHostname = detectLanIp();
        System.setProperty("java.rmi.server.hostname", rmiHostname);
        System.out.println("[rmi] hostname → " + rmiHostname);

        int tcpPort = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_TCP_PORT;
        int rmiPort = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_RMI_PORT;

        BlockingQueue<ClientMessage> queue    = new LinkedBlockingQueue<>();
        ServerRegistry               registry = new ServerRegistry();
        registry.startHeartbeat(); // start heartbeat thread

        // create game persistence
        GamePersistence persistence = new GamePersistence(Path.of("mesos-save.dat"));
        registry.setPersistence(persistence); // associate persistence with the registry
        persistence.load().ifPresentOrElse( // if a saved game exists, load it into the registry; otherwise do nothing
            g -> {
                String nicks = g.getPlayers().stream()
                        .map(p -> p.getId())
                        .collect(Collectors.joining(", "));
                System.out.println("[recovery] saved game found — players: "
                        + nicks + " — round " + g.getCurrentRound());
                registry.setSavedGame(g);
            },
            () -> System.out.println("[persistence] no save file found — normal lobby")
        );

        if (args.length >= 3) { // optional MySQL connection parameters
            String mysqlHost = args[2]; // address of the MySQL server
            String dbUser    = args.length >= 4 ? args[3] : "root";
            String dbPass    = args.length >= 5 ? args[4] : "";
            try { // open TCP connection to MySQL
                ScoreRepository repo = new ScoreRepository(mysqlHost, dbUser, dbPass); // create ScoreRepository: creates DB if missing, then connects
                registry.setRepository(repo); // pass repository to ServerRegistry so it can
                // save results and build the RankingMessage at game-over
                Runtime.getRuntime().addShutdownHook(new Thread(repo::close));
                // shutdown hook: JVM runs this automatically on exit to close the MySQL TCP connection cleanly
                System.out.println("[db] connected to " + ScoreRepository.DB_NAME + " on " + mysqlHost);
            } catch (Exception e) { // server unreachable
                System.err.println("[db] unavailable — leaderboard disabled: " + e.getMessage());
            }
        }

        // Controller worker
        Thread worker = new Thread(new ControllerWorker(queue, registry), "controller-worker");
        worker.setDaemon(true);
        worker.start();

        // TCP accept loop
        ServerSocket serverSocket = new ServerSocket(tcpPort);
        System.out.println("[tcp] in ascolto sulla porta " + tcpPort);
        Thread tcpAccept = new Thread(() -> acceptLoop(serverSocket, queue, registry), "tcp-accept"); // loop to accept incoming socket connections
        tcpAccept.setDaemon(true);
        tcpAccept.start();

        // RMI registry
        try {
            RemoteGameServiceImpl rmiService = new RemoteGameServiceImpl(queue, registry); // instantiate with queue and registry
            LocateRegistry.createRegistry(rmiPort).rebind(RMI_BINDING, rmiService); // create registry on RMI port and bind to rmiService
            System.out.println("[rmi] registry avviato sulla porta " + rmiPort);
        } catch (Exception e) {
            System.out.println("[rmi] non disponibile: " + e.getMessage() + " — solo TCP");
        }

        Thread.currentThread().join();
    }

    /**
     * Returns the LAN IP this machine would use to reach the internet.
     * Uses a UDP socket routed toward 8.8.8.8 — no actual packet is sent,
     * but the OS picks the correct outbound interface, giving us the LAN IP.
     */
    private static String detectLanIp() {
        try (java.net.DatagramSocket s = new java.net.DatagramSocket()) {
            s.connect(java.net.InetAddress.getByName("8.8.8.8"), 80);
            return s.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private static void acceptLoop(ServerSocket serverSocket,
                                   BlockingQueue<ClientMessage> queue,
                                   ServerRegistry registry) {
        while (!serverSocket.isClosed()) {
            try { // for each incoming TCP connection, spawn a TcpClientHandler thread
                Socket socket = serverSocket.accept();
                System.out.println("[tcp] connection from " + socket.getRemoteSocketAddress());
                Thread t = new Thread(new TcpClientHandler(socket, queue, registry), // receive connection and start handler thread
                        "handler-" + socket.getRemoteSocketAddress()); // thread name for the TcpClientHandler
                t.setDaemon(true); // daemon thread
                t.start();
            } catch (IOException e) {
                if (!serverSocket.isClosed())
                    System.err.println("[tcp] accept error: " + e.getMessage());
            }
        }
    }
}
