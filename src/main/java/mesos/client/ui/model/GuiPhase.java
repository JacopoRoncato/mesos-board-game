package mesos.client.ui.model;

/**
 * Local UI state describing what the player's client expects to do next.
 * Derived from {@link mesos.network.server.SnapshotMessage} by
 * {@link PhaseController}; consumed by the panels to decide what to
 * highlight, enable, or disable.
 *
 * <p>This is intentionally distinct from {@code mesos.enums.GamePhase}
 * (the server-side model phase) — the GUI tracks finer-grained substates
 * such as "my turn vs. other player's turn within the same model phase".
 */
public enum GuiPhase {

    /** I just connected; I'm picking my totem colour in the lobby. */
    LOBBY_PICKING_COLOR,

    /** I picked a colour; waiting for the other players to pick theirs. */
    LOBBY_WAITING_OTHERS,

    /** Lobby is full and all colours picked — anyone can press Start. */
    READY_TO_START,

    /** PLACEMENT phase on the server, and the local player still has to place. */
    PLACEMENT_MINE,

    /** PLACEMENT phase on the server, and the local player already placed. */
    PLACEMENT_WAITING,

    /** ACTION_RESOLUTION on the server, my turn to execute card picks. */
    ACTION_MINE,

    /** Just finished EXECUTE, my B020 bonus pick is now active. */
    ACTION_EXTRA_PICK_MINE,

    /** ACTION_RESOLUTION on the server, but I'm done — others still executing. */
    ACTION_DONE_WAITING,

    /** Server is resolving final-era scoring; show animation/wait screen. */
    FINAL_SCORING,

    /** Match concluded — show ranking + new-game button. */
    GAME_OVER;

    public boolean isLobby() {
        return this == LOBBY_PICKING_COLOR
            || this == LOBBY_WAITING_OTHERS
            || this == READY_TO_START;
    }

    public boolean isPlaying() {
        return this == PLACEMENT_MINE
            || this == PLACEMENT_WAITING
            || this == ACTION_MINE
            || this == ACTION_EXTRA_PICK_MINE
            || this == ACTION_DONE_WAITING;
    }

    public boolean isMyTurn() {
        return this == LOBBY_PICKING_COLOR
            || this == PLACEMENT_MINE
            || this == ACTION_MINE
            || this == ACTION_EXTRA_PICK_MINE;
    }
}
