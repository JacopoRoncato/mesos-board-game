package mesos.client.ui.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import mesos.network.server.SnapshotMessage;

/**
 * Local-only state machine that derives a {@link GuiPhase} from the
 * latest server snapshot. Panels subscribe via
 * {@link #addListener(Runnable)} and re-read this controller after each
 * change to decide what to highlight, enable, or display.
 *
 * <p>Threading: {@link #update(SnapshotMessage)} must be called on the EDT.
 * Listeners are invoked synchronously on the calling thread (the EDT).
 *
 * <p>Transport-agnostic: the controller only consumes {@link SnapshotMessage}
 * plus the local nickname, so it works identically over TCP or RMI.
 */
public final class PhaseController {

    private final String myNickname;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private GuiPhase guiPhase = GuiPhase.LOBBY_PICKING_COLOR;
    private SnapshotMessage lastSnapshot;
    private PicksRemaining remainingPicks = new PicksRemaining(0, 0);
    private Set<String> takenColors = Set.of();
    private int topBudget = 0;
    private int botBudget = 0;
    private final java.util.List<String> picks = new java.util.ArrayList<>();

    public PhaseController(String myNickname) {
        this.myNickname = Objects.requireNonNull(myNickname, "nickname");
    }

    public String getMyNickname() { return myNickname; }
    public GuiPhase getGuiPhase() { return guiPhase; }
    public PicksRemaining getRemainingPicks() { return remainingPicks; }
    public SnapshotMessage getLastSnapshot() { return lastSnapshot; }

    /**
     * Returns the nickname of the player whose turn it currently is, or
     * {@code null} during lobby, waiting, or end-game phases.
     *
     * <p>The server does not expose an explicit "active player" field in
     * the snapshot. We approximate it from the local {@link GuiPhase}: when
     * it is this client's turn ({@link GuiPhase#isMyTurn()}), we return our
     * own nickname so that {@link mesos.client.ui.panel.PlayerPanel} can highlight us with the
     * {@code *} marker.
     */
    public String getActivePlayerNickname() {
        return guiPhase.isMyTurn() ? myNickname : null;
    }

    /**
     * True when the local player can click the given tile to place
     * their totem. Returns false outside PLACEMENT_MINE, or when the
     * tile is already occupied.
     */
    public boolean isTileClickable(String tileId) {
        if (guiPhase != GuiPhase.PLACEMENT_MINE) return false;
        if (lastSnapshot == null) return false;
        return lastSnapshot.getOfferTrack().stream()
                .filter(t -> tileId.equals(t.getTileId()))
                .findFirst()
                .map(t -> "free".equals(t.getOccupant()))
                .orElse(false);
    }

    public void addListener(Runnable r) { listeners.add(Objects.requireNonNull(r)); }
    public void removeListener(Runnable r) { listeners.remove(r); }

    /** Visible for tests only; production code must go through {@link #update}. */
    void setGuiPhaseForTest(GuiPhase p) { transitionTo(p); }

    private void transitionTo(GuiPhase next) {
        if (next == guiPhase) return;
        guiPhase = next;
        for (Runnable r : listeners) r.run();
    }

    /**
     * Called by the network layer (or directly by the GameSnapshot listener)
     * every time a new server snapshot arrives. Computes the new GuiPhase
     * and notifies subscribers if it changed.
     */
    public void update(SnapshotMessage snap) {
        this.lastSnapshot = Objects.requireNonNull(snap, "snap");
        GuiPhase next = derive(snap);
        if (next == GuiPhase.ACTION_MINE && guiPhase != GuiPhase.ACTION_MINE) {
            // Newly my action turn: derive the pick budget from my tile, fresh selection.
            // Cap against selectable slots: if a row has only events, budget becomes 0
            // so picksComplete() returns true immediately and "Esegui" is enabled.
            String tile = myTile(snap);
            topBudget = Math.min(tileTop(tile), countSelectable(snap.getTopRow()));
            botBudget = Math.min(tileBot(tile), countSelectable(snap.getBottomRow()));
            picks.clear();
            recomputeRemaining();
        } else if (next != GuiPhase.ACTION_MINE && next != GuiPhase.ACTION_EXTRA_PICK_MINE
                   && !picks.isEmpty()) {
            // Exiting pick phase: clear selections to avoid showing
            // red borders on cards in non-interactive phases (e.g. PLACEMENT_WAITING).
            picks.clear();
            recomputeRemaining();
        }
        transitionTo(next);
    }

    private GuiPhase derive(SnapshotMessage snap) {
        String phase = snap.getPhase();
        if (phase == null) return guiPhase;
        // Phase strings here are the server enum names: SnapshotMessage.from(game)
        // serializes game.getPhase().name() into snap.phase. Cross-package coupling
        // is by string on purpose so the client UI stays decoupled from mesos.enums.GamePhase.
        return switch (phase) {
            case "PLACEMENT"          -> isMySequentialTurn(snap) ? GuiPhase.PLACEMENT_MINE
                                                               : GuiPhase.PLACEMENT_WAITING;
            case "ACTION_RESOLUTION"  -> {
                // extra pick (B020) takes priority: server says who is waiting for it
                if (myNickname.equals(snap.getExtraPickPendingId())) yield GuiPhase.ACTION_EXTRA_PICK_MINE;
                String executor = snap.getCurrentExecutionPlayerId();
                yield myNickname.equals(executor) ? GuiPhase.ACTION_MINE : GuiPhase.ACTION_DONE_WAITING;
            }
            // END_OF_ROUND: server is resolving events between rounds — show a neutral waiting state
            // so panels don't keep showing ACTION_MINE with clickable cards during resolution.
            case "END_OF_ROUND"       -> GuiPhase.ACTION_DONE_WAITING;
            case "FINAL_SCORING"      -> GuiPhase.FINAL_SCORING;
            case "GAME_OVER"          -> GuiPhase.GAME_OVER;
            default                   -> guiPhase;  // lobby / unknown — no change
        };
    }

    /**
     * Returns the lower-case view-colour names ({@code red/yellow/cyan/
     * purple/white}) that this client may NOT pick. Driven by the lobby
     * handshake via {@link #setTakenColors(java.util.Set)}.
     */
    public Set<String> takenColors() {
        return takenColors;
    }

    /** Lobby handshake feeds the colours already taken / unavailable. View-only. */
    public void setTakenColors(Set<String> taken) {
        this.takenColors = Set.copyOf(taken);
        for (Runnable r : listeners) r.run();
    }

    /** Lets the client drive a lobby sub-phase (colour-pick → ready-to-start). View-only. */
    public void setLobbyPhase(GuiPhase p) {
        if (p.isLobby()) transitionTo(p);
    }

    private boolean myTotemPlaced(SnapshotMessage snap) {
        return snap.getPlayers().stream()
                .filter(p -> myNickname.equals(p.getId()))
                .findFirst()
                .map(p -> p.getSelectedTile() != null && !"-".equals(p.getSelectedTile()))
                .orElse(false);
    }

    private boolean isMySequentialTurn(SnapshotMessage snap) {
        return snap.getPlayers().stream()
                .filter(p -> "-".equals(p.getSelectedTile()))
                .findFirst()
                .map(p -> myNickname.equals(p.getId()))
                .orElse(false);
    }

    // ── Action-phase card picking ─────────────────────────────────────────────

    private String myTile(SnapshotMessage snap) {
        return snap.getPlayers().stream()
                .filter(p -> myNickname.equals(p.getId()))
                .map(SnapshotMessage.PlayerSnapshot::getSelectedTile)
                .findFirst().orElse("-");
    }

    /** Cards this tile lets you take from the TOP row (mirrors the server's tile rules). */
    private static int countSelectable(List<SnapshotMessage.SlotSnapshot> row) {
        return (int) row.stream()
                .filter(s -> !"EMPTY".equals(s.getKind())
                          && !"EVENT".equals(s.getKind())
                          && !"FINAL_EVENT".equals(s.getKind()))
                .count();
    }

    private static int tileTop(String t) {
        return switch (t == null ? "" : t) { case "C", "E" -> 1; case "F", "G" -> 2; default -> 0; };
    }
    private static int tileBot(String t) {
        return switch (t == null ? "" : t) { case "B", "E", "G" -> 1; case "D" -> 2; default -> 0; };
    }

    /** True during a phase where the local player picks cards from the rows. */
    public boolean isActionPick() {
        return guiPhase == GuiPhase.ACTION_MINE || guiPhase == GuiPhase.ACTION_EXTRA_PICK_MINE;
    }
    /** True when the given row currently offers picks for this client's tile. */
    public boolean rowPickable(boolean top) {
        return isActionPick() && (top ? topBudget : botBudget) > 0;
    }
    public boolean isPicked(boolean top, int index) {
        return picks.contains(tok(top, index));
    }
    /** Toggle a card selection, bounded by the row budget. Fires listeners. */
    public void togglePick(boolean top, int index) {
        if (!rowPickable(top)) return;
        String t = tok(top, index);
        if (picks.contains(t)) {
            picks.remove(t);
        } else {
            long used = picks.stream().filter(s -> s.startsWith(top ? "TOP:" : "BOTTOM:")).count();
            if (used >= (top ? topBudget : botBudget)) return;   // row already full
            picks.add(t);
        }
        recomputeRemaining();
        for (Runnable r : listeners) r.run();
    }
    /** Space-joined pick tokens for the EXECUTE command, e.g. "TOP:0 BOTTOM:2". */
    public String execArgs() { return String.join(" ", picks); }
    public int totalPickBudget() { return topBudget + botBudget; }
    public boolean picksComplete() {
        return remainingPicks.top() == 0 && remainingPicks.bottom() == 0;
    }

    private void recomputeRemaining() {
        long ut = picks.stream().filter(s -> s.startsWith("TOP:")).count();
        long ub = picks.stream().filter(s -> s.startsWith("BOTTOM:")).count();
        remainingPicks = new PicksRemaining((int) (topBudget - ut), (int) (botBudget - ub));
    }
    private static String tok(boolean top, int i) { return (top ? "TOP:" : "BOTTOM:") + i; }
}
