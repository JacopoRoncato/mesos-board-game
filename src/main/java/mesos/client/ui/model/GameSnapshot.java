package mesos.client.ui.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import mesos.client.ui.widget.CardComponent;
import mesos.enums.CharacterType;
import mesos.network.server.SnapshotMessage;

/**
 * Client-side mirror of the server state. After the colleagues' refactor the
 * server pushes a full {@link SnapshotMessage} on every model change, so this
 * class is now a plain projector of that DTO: no more line parsing.
 *
 * UI components subscribe via {@link #addListener(Runnable)} and receive
 * callbacks on the calling thread. {@link #applySnapshot(SnapshotMessage)}
 * must be called from the EDT for GUI clients.
 */
public class GameSnapshot {

    public enum Phase {
        SETUP, PLACEMENT, ACTION_RESOLUTION, END_OF_ROUND, FINAL_SCORING, GAME_OVER, UNKNOWN
    }

    public static class PlayerState {
        public final String id;
        public int food = 0;
        public int prestige = 0;
        public String selectedTile = "-";
        /** TotemColor enum name as sent by the server (e.g. "RED", "BLUE", "YELLOW"). */
        public String totemColor = null;
        /** Acquired tribe cards (characters + buildings); public info in MESOS. Never null. */
        public List<TribeCard> tribe = Collections.emptyList();

        public PlayerState(String id) { this.id = id; }
    }

    /** One acquired card in a player's tribe. {@code type} is null for buildings. */
    public static class TribeCard {
        public final CardComponent.Kind kind;  // CHARACTER or BUILDING
        public final String name;
        public final CharacterType type;        // null for buildings
        public TribeCard(CardComponent.Kind kind, String name, CharacterType type) {
            this.kind = kind;
            this.name = name == null ? "" : name;
            this.type = type;
        }
    }

    /** One slot in a visible row. {@code kind == EMPTY} means no card. */
    public static class CardInfo {
        public final CardComponent.Kind kind;
        public final String name;
        public CardInfo(CardComponent.Kind kind, String name) {
            this.kind = kind;
            this.name = name == null ? "" : name;
        }
        public static final CardInfo EMPTY = new CardInfo(CardComponent.Kind.EMPTY, "");
    }

    private Phase phase = Phase.UNKNOWN;
    private int round = 0;
    private String era = "?";
    private String myNickname = null;
    private final Map<String, PlayerState> players = new LinkedHashMap<>();
    private List<CardInfo> topRow = Collections.emptyList();
    private List<CardInfo> bottomRow = Collections.emptyList();
    private final Map<String, String> tiles = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    // ── public read-only API ────────────────────────────────────────────────

    public Phase getPhase()      { return phase; }
    public int getRound()        { return round; }
    public String getEra()       { return era; }
    public String getMyNickname(){ return myNickname; }
    public PlayerState getPlayer(String id) { return players.get(id); }
    public Collection<PlayerState> getPlayers() { return players.values(); }

    /** Returns the nicknames of all known players, preserving insertion order. */
    public List<String> getPlayerNicks() {
        return players.values().stream()
                .map(p -> p.id)
                .collect(Collectors.toList());
    }
    public List<CardInfo> getTopRow()    { return topRow; }
    public List<CardInfo> getBottomRow() { return bottomRow; }
    public Map<String, String> getTiles() { return tiles; }

    public PlayerState ensurePlayer(String id) {
        return players.computeIfAbsent(id, PlayerState::new);
    }

    public void addListener(Runnable r) {
        if (r != null) listeners.add(r);
    }

    /** Called once after lobby completes so PlayerPanel/StatusBar know who I am. */
    public void setMyNickname(String nick) {
        this.myNickname = nick;
        fire();
    }

    /** Applies a server-pushed snapshot. EDT-required for GUI consumers. */
    public void applySnapshot(SnapshotMessage snap) {
        if (snap == null) return;

        this.phase = parsePhase(snap.getPhase());
        this.round = snap.getRound();
        this.era   = snap.getEra();

        this.topRow    = mapRow(snap.getTopRow());
        this.bottomRow = mapRow(snap.getBottomRow());

        tiles.clear();
        for (SnapshotMessage.TileSnapshot t : snap.getOfferTrack()) {
            tiles.put(t.getTileId(), t.getOccupant());
        }

        // Players: keep insertion order matching the server.
        Map<String, PlayerState> next = new LinkedHashMap<>();
        for (SnapshotMessage.PlayerSnapshot p : snap.getPlayers()) {
            PlayerState ps = players.getOrDefault(p.getId(), new PlayerState(p.getId()));
            ps.food = p.getFood();
            ps.prestige = p.getPrestigePoints();
            ps.selectedTile = p.getSelectedTile();
            ps.totemColor = p.getTotemColor();
            ps.tribe = mapTribe(p.getTribe());
            next.put(p.getId(), ps);
        }
        players.clear();
        players.putAll(next);

        fire();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static List<CardInfo> mapRow(List<SnapshotMessage.SlotSnapshot> slots) {
        List<CardInfo> out = new ArrayList<>();
        for (SnapshotMessage.SlotSnapshot s : slots) {
            out.add(new CardInfo(kindFor(s.getKind()), s.getName()));
        }
        return out;
    }

    private static List<TribeCard> mapTribe(List<SnapshotMessage.TribeCardRef> refs) {
        if (refs == null || refs.isEmpty()) return Collections.emptyList();
        List<TribeCard> out = new ArrayList<>();
        for (SnapshotMessage.TribeCardRef r : refs) {
            CharacterType type = null;
            if (r.getType() != null) {
                // Unresolvable type cannot happen under matched serialVersionUID (client and
                // server share the same CharacterType enum); type stays null only on a forged
                // message, in which case the card is shown but not per-type counted.
                try { type = CharacterType.valueOf(r.getType()); }
                catch (IllegalArgumentException ignored) { }
            }
            out.add(new TribeCard(kindFor(r.getKind()), r.getName(), type));
        }
        return out;
    }

    private static CardComponent.Kind kindFor(String k) {
        if (k == null) return CardComponent.Kind.EMPTY;
        return switch (k) {
            case "CHARACTER"   -> CardComponent.Kind.CHARACTER;
            case "BUILDING"    -> CardComponent.Kind.BUILDING;
            case "EVENT"       -> CardComponent.Kind.EVENT;
            case "FINAL_EVENT" -> CardComponent.Kind.BACK;
            default            -> CardComponent.Kind.EMPTY;
        };
    }

    private static Phase parsePhase(String s) {
        if (s == null) return Phase.UNKNOWN;
        try { return Phase.valueOf(s); }
        catch (IllegalArgumentException e) { return Phase.UNKNOWN; }
    }

    private void fire() {
        for (Runnable r : listeners) r.run();
    }
}
