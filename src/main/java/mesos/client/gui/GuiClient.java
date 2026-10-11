package mesos.client.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import mesos.db.ScoreResult;
import mesos.client.Transport;
import mesos.client.ui.model.GameSnapshot;
import mesos.client.ui.model.GuiPhase;
import mesos.client.ui.model.PhaseController;
import mesos.client.ui.panel.ActionPanel;
import mesos.client.ui.panel.BoardPanel;
import mesos.client.ui.panel.CountPanel;
import mesos.client.ui.panel.DisconnectPanel;
import mesos.client.ui.panel.EndGamePanel;
import mesos.client.ui.panel.LobbyPanel;
import mesos.client.ui.panel.PlayerPanel;
import mesos.client.ui.panel.StatusBar;
import mesos.client.ui.resource.PlayerColors;
import mesos.client.ui.resource.Theme;
import mesos.client.ui.widget.ActionPicksBar;
import mesos.client.ui.widget.ExtraPickBanner;
import mesos.client.ui.widget.TotemBadge;
import mesos.enums.TotemColor;
import mesos.network.client.CommandMessage;
import mesos.network.client.StartMessage;
import mesos.network.server.ErrMessage;
import mesos.network.server.HeartbeatMessage;
import mesos.network.server.InfoMessage;
import mesos.network.server.OkMessage;
import mesos.network.server.RankingMessage;
import mesos.network.server.ServerMessage;
import mesos.network.server.SnapshotMessage;

/**
 * Swing client for MESOS, wired onto branch {@code test}'s {@link Transport}
 * protocol. Pure client/view glue: it adapts the imported {@code mesos.client.ui}
 * view to the existing serialized-message transport without touching game logic.
 *
 * <p>Lobby handshake follows {@code TuiClient}: {@link Transport#connect} performs
 * login + player-count + colour selection via the {@code askNumPlayers}/{@code askColor}
 * callbacks (run here as Swing dialogs). The server then pushes a full
 * {@link SnapshotMessage} after every model mutation; the GUI renders from it.
 */
public final class GuiClient {

    private static final String CARD_LOBBY     = "LOBBY";
    private static final String CARD_GAME      = "GAME";
    private static final String CARD_GAME_OVER = "GAMEOVER";
    private static final String CARD_END       = "END";
    private static final String CARD_DISC      = "DISC";
    private static final String CARD_COUNT     = "COUNT";

    private final Transport transport;
    private final String    host;
    private final int       port;

    private final GameSnapshot snapshot = new GameSnapshot();
    private final JTextArea    logArea  = new JTextArea();
    private JScrollPane logScroll;
    private JPanel      playersStack;
    private JSplitPane  mainSplit;

    private JFrame     frame;
    private CardLayout cards;
    private JPanel     rootCards;

    private LobbyPanel      lobbyPanel;
    private JPanel          gamePanel;
    private EndGamePanel    endGamePanel;
    private DisconnectPanel disconnectPanel;
    private CountPanel      countPanel;

    private PhaseController phaseController;

    /** Saved when RankingMessage arrives; passed to EndGamePanel on "Vedi classifica" click. */
    private List<ScoreResult> pendingRanking;

    private String  myNickname = "";
    /** EDT-confined: mutated only from the snapshot listener, which always runs inside {@code invokeLater}. */
    private boolean panelsBuilt = false;
    /** True when the window is borderless true-fullscreen (toggled with F11/Esc). */
    private boolean fullscreen = false;
    private volatile long lastMsg = System.currentTimeMillis();

    /** Lobby colour handshake: the connector thread blocks here until "Conferma". */
    private final BlockingQueue<TotemColor> colorPick = new ArrayBlockingQueue<>(1);
    /** Colours the server offered this client (set when {@link #askColor} runs). */
    private volatile List<TotemColor> availableColors = List.of();
    /** Lobby player-count step: the connector thread blocks here until a count button. */
    private final BlockingQueue<Integer> countPick = new ArrayBlockingQueue<>(1);
    private GuiClient(Transport transport, String host, int port) {
        this.transport = transport;
        this.host = host;
        this.port = port;
    }
    /** Entry point used by {@link mesos.client.Launcher}. Builds the UI on the EDT. */
    public static void start(Transport transport, String host, int port) {
        SwingUtilities.invokeLater(() -> new GuiClient(transport, host, port).launch());
    }

    /** Standalone launch: {@code java mesos.client.gui.GuiClient [host] [port]} (TCP). */
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int    port = args.length > 1 ? Integer.parseInt(args[1]) : 1234;
        start(new mesos.client.TcpTransport(), host, port);
    }

    // ── UI skeleton ──────────────────────────────────────────────────────────

    private void launch() {
        frame = new JFrame("MESOS");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.setUndecorated(true);          // borderless — required for true fullscreen
        frame.setMinimumSize(new Dimension(900, 600));

        cards     = new CardLayout();
        rootCards = new JPanel(cards);
        rootCards.setOpaque(false);
        frame.add(rootCards, BorderLayout.CENTER);

        snapshot.addListener(this::onSnapshotChange);
        wireNetworkReader();
        installFullscreenKeys();             // F11 = toggle fullscreen, Esc = leave it

        // Show the window BEFORE connect(): connect() opens a modal nickname
        // dialog parented to this frame and then blocks the EDT on it. If the
        // frame isn't visible/positioned/focused first, that dialog opens
        // off-screen or behind other windows and the frame is never shown.
        frame.pack();
        frame.setVisible(true);
        // MAXIMIZED_BOTH after setVisible: the OS window manager already knows
        // the work area (menu bar + dock on macOS, taskbar on Windows) and places
        // the frame correctly. Querying screen insets before setVisible can return
        // zero on macOS for undecorated frames.
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.toFront();
        frame.requestFocus();
        fullscreen = false;

        connect();
    }

    // ── Fullscreen (borderless) ────────────────────────────────────────────────

    /** Whole primary screen, including the taskbar — true-fullscreen bounds. */
    private static Rectangle fullScreenBounds() {
        Rectangle cfg = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        // getDefaultConfiguration().getBounds() can return the work area (taskbar
        // excluded) on per-monitor-DPI-aware JVMs; Toolkit.getScreenSize() reports
        // the full screen. Take the larger so we always cover the taskbar.
        Dimension tk = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        return new Rectangle(cfg.x, cfg.y,
                Math.max(cfg.width, tk.width), Math.max(cfg.height, tk.height));
    }

    /**
     * Usable area excluding OS chrome (macOS menu bar + dock, Windows taskbar).
     * Toolkit.getScreenInsets(gc) is more reliable than getMaximumWindowBounds()
     * for undecorated windows on macOS, where the dock inset is otherwise ignored.
     */
    private static Rectangle workAreaBounds() {
        java.awt.GraphicsConfiguration gc = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration();
        Rectangle screen = gc.getBounds();
        java.awt.Insets ins = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(
                screen.x + ins.left,
                screen.y + ins.top,
                screen.width  - ins.left - ins.right,
                screen.height - ins.top  - ins.bottom);
    }

    /**
     * Toggle between borderless true-fullscreen (covers the taskbar, no title
     * bar) and a normal maximized decorated window. Changing the decoration
     * requires disposing and re-showing the frame; the content (CardLayout +
     * panels) and the network state persist across the recreate.
     */
    private void applyFullscreen(boolean fs) {
        boolean wasVisible = frame.isDisplayable();
        if (wasVisible) frame.dispose();
        frame.setUndecorated(fs);
        if (fs) {
            frame.setExtendedState(JFrame.NORMAL);
            frame.setBounds(fullScreenBounds());
        } else {
            // Let the OS maximize within the work area (no manual bounds).
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
        fullscreen = fs;
        if (wasVisible) {
            frame.setVisible(true);
            frame.toFront();
            frame.requestFocus();
        }
    }

    /** F11 toggles fullscreen; Esc leaves fullscreen for a normal window. */
    private void installFullscreenKeys() {
        javax.swing.JRootPane rp = frame.getRootPane();
        javax.swing.InputMap im = rp.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        im.put(javax.swing.KeyStroke.getKeyStroke("F11"), "toggleFullscreen");
        im.put(javax.swing.KeyStroke.getKeyStroke("ESCAPE"), "exitFullscreen");
        rp.getActionMap().put("toggleFullscreen", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { applyFullscreen(!fullscreen); }
        });
        rp.getActionMap().put("exitFullscreen", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { if (fullscreen) applyFullscreen(false); }
        });
    }

    private void buildCards() {
        rootCards.removeAll();

        lobbyPanel   = new LobbyPanel(phaseController, this::sendColor, this::sendStart);
        gamePanel    = buildGamePanel();
        endGamePanel = new EndGamePanel(snapshot, this::onExit);
        disconnectPanel = new DisconnectPanel(
                DisconnectPanel.Mode.SERVER_UNREACHABLE,
                "In attesa di connessione...",
                () -> {}, this::onExit);
        countPanel = new CountPanel(this::onCountChosen);

        rootCards.add(lobbyPanel,            CARD_LOBBY);
        rootCards.add(countPanel,            CARD_COUNT);
        rootCards.add(gamePanel,             CARD_GAME);
        rootCards.add(buildGameOverBanner(), CARD_GAME_OVER);
        rootCards.add(endGamePanel,          CARD_END);
        rootCards.add(disconnectPanel,       CARD_DISC);

        rootCards.revalidate();
        rootCards.repaint();

        phaseController.addListener(this::switchCardForPhase);
        switchCardForPhase();
    }

    private JPanel buildGamePanel() {
        JPanel game = new JPanel(new BorderLayout(0, 0));
        game.setOpaque(false);

        StatusBar status = new StatusBar(snapshot, phaseController);
        game.add(status, BorderLayout.NORTH);

        BoardPanel board = new BoardPanel(snapshot, phaseController, this::onPlaceTile);

        JPanel rightColumn = new JPanel(new BorderLayout(0, Theme.scale(6)));
        rightColumn.setOpaque(false);
        rightColumn.setBorder(BorderFactory.createEmptyBorder(Theme.scale(8), Theme.scale(4), Theme.scale(8), Theme.scale(8)));

        playersStack = new JPanel();
        playersStack.setLayout(new BoxLayout(playersStack, BoxLayout.Y_AXIS));   // accordion: panels at preferred height
        playersStack.setOpaque(false);

        JScrollPane playersScroll = new JScrollPane(playersStack,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        playersScroll.setBorder(BorderFactory.createEmptyBorder());
        playersScroll.setOpaque(false);
        playersScroll.getViewport().setOpaque(false);
        rightColumn.add(playersScroll, BorderLayout.CENTER);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Theme.scale(11)));
        logArea.setBackground(Theme.CREAM);
        logArea.setForeground(Theme.INK);
        logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Eventi (debug)"));
        logScroll.setVisible(false);
        // debug log is always hidden; kept as a field but not added to the layout

        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, board, rightColumn);
        mainSplit.setResizeWeight(0.85);
        mainSplit.setBorder(BorderFactory.createEmptyBorder());
        mainSplit.setBackground(new Color(0x2E, 0x1F, 0x14));
        game.add(mainSplit, BorderLayout.CENTER);

        // BoxLayout instead of GridLayout: setVisible(false) on a GridLayout child
        // reserves its slot anyway, leaving a permanent empty gap when the banner hides.
        // BoxLayout collapses the space correctly.
        JPanel topBadges = new JPanel();
        topBadges.setLayout(new BoxLayout(topBadges, BoxLayout.Y_AXIS));
        topBadges.setOpaque(false);
        topBadges.add(new ActionPicksBar(phaseController));
        topBadges.add(new ExtraPickBanner(phaseController, this::sendSkipExtra));

        ActionPanel ap = new ActionPanel(phaseController, this::sendVerb,
                                         this::sendStart, this::sendSkipExtra, this::onExit);

        JPanel bottomStack = new JPanel(new BorderLayout());
        bottomStack.setOpaque(false);
        bottomStack.add(topBadges, BorderLayout.NORTH);
        bottomStack.add(ap, BorderLayout.SOUTH);
        game.add(bottomStack, BorderLayout.SOUTH);

        return game;
    }

    private void switchCardForPhase() {
        if (cards == null || rootCards == null) return;
        GuiPhase p = phaseController.getGuiPhase();
        String name;
        if (p.isLobby()) {
            name = CARD_LOBBY;
        } else if (p == GuiPhase.GAME_OVER) {
            name = CARD_GAME_OVER;
        } else if (p == GuiPhase.FINAL_SCORING) {
            name = CARD_GAME;
        } else {
            name = CARD_GAME;
        }
        cards.show(rootCards, name);
        // Defensive: the count picker is only valid on its own card; ensure its
        // buttons can never linger behind another card on incremental repaints.
        if (countPanel != null) countPanel.setVisible(CARD_COUNT.equals(name));
        rootCards.repaint();
    }

    private void showDisconnect(DisconnectPanel.Mode mode, String detail,
                                Runnable primary, Runnable secondary) {
        if (disconnectPanel != null) rootCards.remove(disconnectPanel);
        disconnectPanel = new DisconnectPanel(mode, detail, primary, secondary);
        rootCards.add(disconnectPanel, CARD_DISC);
        cards.show(rootCards, CARD_DISC);
        rootCards.revalidate();
        rootCards.repaint();
    }

    private void onSnapshotChange() {
        if (!panelsBuilt && !snapshot.getPlayers().isEmpty()) buildPlayerPanels();
    }

    private void buildPlayerPanels() {
        if (playersStack == null) return;
        playersStack.removeAll();
        boolean first = true;
        for (GameSnapshot.PlayerState p : snapshot.getPlayers()) {
            if (!first) playersStack.add(javax.swing.Box.createVerticalStrut(Theme.scale(8)));
            first = false;
            // Use the real totem colour chosen in the lobby, not a position-based fallback.
            PlayerPanel pp = new PlayerPanel(p.id, PlayerColors.fromServerColor(p.totemColor),
                                             snapshot, phaseController);
            pp.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            playersStack.add(pp);
        }
        playersStack.revalidate();
        playersStack.repaint();
        panelsBuilt = true;
    }

    // ── Network reader ─────────────────────────────────────────────────────────

    private void wireNetworkReader() {
        transport.setOnMessage(msg -> {
            lastMsg = System.currentTimeMillis();
            SwingUtilities.invokeLater(() -> handleServerMessage(msg));
        });
    }

    private void handleServerMessage(ServerMessage msg) {
        if (msg instanceof HeartbeatMessage) return;
        appendLog("<<< " + msg);
        if (msg instanceof SnapshotMessage snap) {
            snapshot.applySnapshot(snap);
            if (phaseController != null) phaseController.update(snap);
        } else if (msg instanceof InfoMessage info) {
            appendLog("[lobby] You are '" + info.getNick() + "' — player "
                    + info.getPlayerIndex() + "/" + info.getTotalPlayers());
        } else if (msg instanceof OkMessage ok) {
            if (ok.getText().startsWith("RECOVERY_JOINED") && lobbyPanel != null) {
                String progress = ok.getText().substring("RECOVERY_JOINED ".length());
                String[] parts  = progress.split("/");
                if (parts.length == 2) {
                    try {
                        lobbyPanel.setRecoveryMode(
                                Integer.parseInt(parts[0].trim()),
                                Integer.parseInt(parts[1].trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }
            appendLog("[OK] " + ok.getText());
        } else if (msg instanceof RankingMessage rm) {
            appendLog("[ranking] " + rm.getEntries());
            pendingRanking = rm.getEntries();
        } else if (msg instanceof ErrMessage err) {
            String reason = err.getReason();
            appendLog("[ERR] " + reason);
            boolean gameAlreadyOver = phaseController != null
                    && (phaseController.getGuiPhase() == GuiPhase.GAME_OVER
                        || phaseController.getGuiPhase() == GuiPhase.FINAL_SCORING);
            if (reason != null && (reason.contains("(connection lost)")
                    || reason.startsWith("PLAYER_DISCONNECTED")
                    || reason.startsWith("RECOVERY_TIMEOUT"))) {
                if (gameAlreadyOver) return;
                showDisconnect(DisconnectPanel.Mode.OTHER_DISCONNECTED,
                        "La partita è terminata: " + reason,
                        () -> {}, this::onExit);
            } else if ("WAITING_FOR_COLORS".equals(reason)
                    || (reason != null && reason.startsWith("NOT_ENOUGH_PLAYERS"))) {
                JOptionPane.showMessageDialog(frame,
                        "Aspetta che tutti i giocatori scelgano un colore prima di iniziare.",
                        "Lobby", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(frame, "Errore dal server: " + reason,
                        "Errore", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    // ── connect (background thread; never blocks the EDT) ───────────────────────

    private void connect() {
        String nick = System.getProperty("mesos.nick");
        if (nick == null || nick.isBlank()) {
            nick = JOptionPane.showInputDialog(frame, "Scegli un nickname:",
                    "MESOS — Lobby", JOptionPane.QUESTION_MESSAGE);
        }
        if (nick == null || nick.isBlank()) nick = "Player";
        myNickname = nick.trim();
        frame.setTitle("MESOS — " + myNickname);
        snapshot.setMyNickname(myNickname);

        phaseController = new PhaseController(myNickname);
        buildCards();

        Thread connector = new Thread(() -> {
            try {
                transport.connect(host, port, myNickname,
                        this::askNumPlayers, this::askColor);
                startWatchdog();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> showDisconnect(
                        DisconnectPanel.Mode.SERVER_UNREACHABLE,
                        "Impossibile connettersi a " + host + ":" + port + ". "
                      + ex.getMessage() + " Verifica che ServerMain sia in esecuzione.",
                        () -> {}, this::onExit));
            }
        }, "gui-connect");
        connector.setDaemon(true);
        connector.start();
    }

    /** {@link Supplier} for {@code connect}: first client picks the count on the lobby (no pop-up). */
    private Integer askNumPlayers() {
        String prop = System.getProperty("mesos.players");
        if (prop != null) {
            try { return clamp(Integer.parseInt(prop.trim())); } catch (NumberFormatException ignored) {}
        }
        countPick.clear();
        SwingUtilities.invokeLater(() -> { if (cards != null) cards.show(rootCards, CARD_COUNT); });
        try {
            return clamp(countPick.take());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 2;
        }
    }

    /** CountPanel button callback: hands the chosen count to the connect handshake. */
    private void onCountChosen(int n) {
        countPick.offer(clamp(n));
    }

    /**
     * Lobby colour handshake — drives the graphical {@link LobbyPanel} instead of a
     * dialog. Marks the unavailable colours, shows the picker, and blocks the
     * connector thread until the player clicks a totem + "Conferma" (which feeds
     * {@link #colorPick} via {@link #sendColor}). The chosen {@link TotemColor} is
     * returned straight to {@code Transport.connect} — no protocol change.
     */
    private TotemColor askColor(List<TotemColor> available) {
        if (available == null || available.isEmpty()) return null;
        // Recovery: server already assigned the only remaining colour — skip the picker UI
        // so the user does not see a misleading full colour grid during reconnection.
        if (available.size() == 1) {
            TotemColor forced = available.get(0);
            SwingUtilities.invokeLater(() -> {
                if (phaseController != null) phaseController.setLobbyPhase(GuiPhase.READY_TO_START);
            });
            return forced;
        }
        this.availableColors = available;
        colorPick.clear();
        SwingUtilities.invokeLater(() -> {
            if (phaseController != null) {
                phaseController.setTakenColors(takenFrom(available));
                phaseController.setLobbyPhase(GuiPhase.LOBBY_PICKING_COLOR);
            }
            if (cards != null) cards.show(rootCards, CARD_LOBBY);
        });
        try {
            TotemColor chosen = colorPick.take();   // wait for "Conferma" on the lobby
            SwingUtilities.invokeLater(() -> {
                if (phaseController != null) phaseController.setLobbyPhase(GuiPhase.READY_TO_START);
            });
            return chosen;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return available.get(0);
        }
    }

    /** Colours not in {@code available} are shown greyed/taken on the lobby cards. */
    private static Set<String> takenFrom(List<TotemColor> available) {
        Set<String> taken = new HashSet<>();
        for (TotemColor c : TotemColor.values()) {
            if (!available.contains(c)) taken.add(viewName(c));
        }
        return taken;
    }

    /** Server enum → LobbyPanel/TotemBadge view name (server {@code BLUE} shows as CYAN). */
    private static String viewName(TotemColor c) {
        return (c == TotemColor.BLUE) ? "cyan" : c.name().toLowerCase(Locale.ROOT);
    }

    /** LobbyPanel view name → server enum (inverse of {@link #viewName}). */
    private static TotemColor fromViewName(String s) {
        if (s == null) return null;
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "red"    -> TotemColor.RED;
            case "yellow" -> TotemColor.YELLOW;
            case "cyan"   -> TotemColor.BLUE;
            case "purple" -> TotemColor.PURPLE;
            case "white"  -> TotemColor.WHITE;
            default       -> null;
        };
    }

    private static int clamp(int n) { return Math.max(2, Math.min(5, n)); }

    private static void runOnEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) { r.run(); return; }
        try { SwingUtilities.invokeAndWait(r); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (InvocationTargetException e) { /* dialog failure → keep default */ }
    }

    private void startWatchdog() {
        Thread watchdog = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try { Thread.sleep(2_000); } catch (InterruptedException e) { return; }
                if (System.currentTimeMillis() - lastMsg > 6_000) {
                    try { transport.close(); } catch (Exception ignored) {}
                    SwingUtilities.invokeLater(() -> showDisconnect(
                            DisconnectPanel.Mode.SERVER_UNREACHABLE,
                            "Connessione al server persa.", () -> {}, this::onExit));
                    return;
                }
            }
        }, "gui-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    // ── Outgoing actions ────────────────────────────────────────────────────────

    /** LobbyPanel "Conferma" callback: hands the chosen colour to the connect handshake. */
    private void sendColor(String colour) {
        TotemColor c = fromViewName(colour);
        if (c == null) return;
        if (!availableColors.contains(c)) {
            appendLog("[colore non disponibile: " + colour + "]");
            return;
        }
        if (colorPick.offer(c)) {
            appendLog("[colore scelto: " + c + "]");
        }
    }

    private void onPlaceTile(String tileId) {
        if (myNickname.isEmpty()) { appendLog("[non sei ancora in partita]"); return; }
        sendVerb("PLACE " + myNickname + " " + tileId);
    }

    private void sendVerb(String cmd) {
        if (cmd == null || cmd.isBlank()) return;
        String trimmed = cmd.trim();
        appendLog(">>> " + trimmed);
        try {
            if (trimmed.equalsIgnoreCase("START")) transport.send(new StartMessage());
            else transport.send(new CommandMessage(trimmed));
        } catch (Exception e) {
            appendLog("[invio fallito] " + e.getMessage());
        }
    }

    private void sendStart() {
        try { transport.send(new StartMessage()); }
        catch (Exception e) { appendLog("[send start] " + e.getMessage()); }
    }

    private void sendSkipExtra() { sendVerb("SKIP_EXTRA " + myNickname); }

    private JPanel buildGameOverBanner() {
        JPanel p = new JPanel(new BorderLayout(0, 24));
        p.setOpaque(true);
        p.setBackground(Theme.NIGHT_DEEP);
        p.setBorder(new EmptyBorder(60, 60, 60, 60));

        JLabel title = new JLabel("PARTITA TERMINATA", JLabel.CENTER);
        title.setFont(new Font("Serif", Font.BOLD, 48));
        title.setForeground(Theme.FLAME);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        buttons.setOpaque(false);
        JButton see = primaryBtn("Vedi classifica");
        see.addActionListener(e -> showRanking());
        JButton exit = secondaryBtn("Esci");
        exit.addActionListener(e -> onExit());
        buttons.add(see);
        buttons.add(exit);

        p.add(title,   BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    private void showRanking() {
        if (pendingRanking != null && endGamePanel != null)
            endGamePanel.setDbRanking(pendingRanking);
        if (cards != null) { cards.show(rootCards, CARD_END); rootCards.repaint(); }
    }

    private static JButton primaryBtn(String s) {
        JButton b = new JButton(s);
        b.setBackground(Theme.FLAME);
        b.setForeground(Theme.INK);
        b.setFont(new Font("Serif", Font.BOLD, 14));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.INK, 3),
                new EmptyBorder(10, 22, 10, 22)));
        b.setFocusPainted(false);
        return b;
    }

    private static JButton secondaryBtn(String s) {
        JButton b = primaryBtn(s);
        b.setBackground(Theme.CREAM);
        return b;
    }

    private void onExit() {
        try { transport.close(); } catch (Exception ignored) {}
        System.exit(0);
    }

    /** Appends a line to the debug log area. Callers must be on the EDT. */
    private void appendLog(String line) {
        logArea.append(line);
        logArea.append("\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}
