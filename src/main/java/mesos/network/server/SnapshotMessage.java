package mesos.network.server;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import mesos.board.RowSlot;
import mesos.board.VisibleRowState;
import mesos.cards.BuildingCard;
import mesos.cards.Card;
import mesos.cards.FinalEventCard;
import mesos.cards.characters.CharacterCard;
import mesos.cards.events.EventCard;
import mesos.enums.VisibleRow;
import mesos.game.Game;
import mesos.offer.OfferTile;
import mesos.player.Player;
import mesos.player.Totem;

/**
 * Full model snapshot sent from server to client after every state change.
 * The client renders entirely from this object — no partial-update logic needed.
 *
 * Built via {@link #from(Game)} on the server; deserialized on the client.
 */
public class SnapshotMessage implements ServerMessage {

    private static final long serialVersionUID = 4L;

    // ── Game-level state ──────────────────────────────────────────────────────
    private final String phase;
    private final int    round;
    private final String era;

    // ── Turn tracking ─────────────────────────────────────────────────────────
    private final String currentPlacementPlayerId;  // null if not in PLACEMENT or all placed
    private final String currentExecutionPlayerId;  // null if not in ACTION_RESOLUTION or all done
    private final String extraPickPendingId;        // non-null while a player awaits their B020 extra pick

    // ── Board ─────────────────────────────────────────────────────────────────
    private final List<SlotSnapshot>  topRow;
    private final List<SlotSnapshot>  bottomRow;
    private final List<TileSnapshot>  offerTrack;

    // ── Players ───────────────────────────────────────────────────────────────
    private final List<PlayerSnapshot> players;

    private SnapshotMessage(String phase, int round, String era,
                            String currentPlacementPlayerId,
                            String currentExecutionPlayerId,
                            String extraPickPendingId,
                            List<SlotSnapshot>  topRow,
                            List<SlotSnapshot>  bottomRow,
                            List<TileSnapshot>  offerTrack,
                            List<PlayerSnapshot> players) {
        this.phase                     = phase;
        this.round                     = round;
        this.era                       = era;
        this.currentPlacementPlayerId  = currentPlacementPlayerId;
        this.currentExecutionPlayerId  = currentExecutionPlayerId;
        this.extraPickPendingId        = extraPickPendingId;
        this.topRow                    = topRow;
        this.bottomRow                 = bottomRow;
        this.offerTrack                = offerTrack;
        this.players                   = players;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /** Builds a SnapshotMessage from the live Game state (server side only). */
    public static SnapshotMessage from(Game game) {
        mesos.player.Player placer   = game.getCurrentPlacementPlayer();
        mesos.player.Player executor = game.getCurrentExecutionPlayer();
        return new SnapshotMessage(
                game.getPhase().name(),
                game.getCurrentRound(),
                game.getCurrentEra().name(),
                placer   != null ? placer.getId()   : null,
                executor != null ? executor.getId() : null,
                game.getExtraPickPendingId(),
                buildRow(game.getBoard().getVisibleRow(VisibleRow.TOP)),
                buildRow(game.getBoard().getVisibleRow(VisibleRow.BOTTOM)),
                buildTiles(game),
                buildPlayers(game)
        );
    }

    private static List<SlotSnapshot> buildRow(VisibleRowState row) {
        List<SlotSnapshot> list = new ArrayList<>();
        for (RowSlot slot : row.getSlots()) {
            list.add(SlotSnapshot.from(slot.getCard()));
        }
        return list;
    }

    private static List<TileSnapshot> buildTiles(Game game) {
        List<TileSnapshot> list = new ArrayList<>();
        for (OfferTile tile : game.getBoard().getOfferTrack().getTiles()) {
            Totem  occ      = tile.getOccupiedBy();
            String occupant = "free";
            if (occ != null) {
                for (Player p : game.getPlayers()) {
                    if (p.getTotem() == occ) { occupant = p.getId(); break; }
                }
            }
            list.add(new TileSnapshot(tile.getId().name(), occupant));
        }
        return list;
    }

    private static List<PlayerSnapshot> buildPlayers(Game game) {
        List<PlayerSnapshot> list = new ArrayList<>();
        for (Player p : game.getPlayers()) {
            String tile = p.getSelectedOfferTile() == null
                    ? "-" : p.getSelectedOfferTile().getId().name();
            list.add(new PlayerSnapshot(p.getId(), p.getFood(), p.getPrestigePoints(), tile,
                    p.getTotem().getColor().name(), buildTribe(p)));
        }
        return list;
    }

    private static List<TribeCardRef> buildTribe(Player p) {
        List<TribeCardRef> tribe = new ArrayList<>();
        for (CharacterCard c : p.getTribe().getCharacterCards()) {
            tribe.add(new TribeCardRef("CHARACTER", c.getName(), c.getType().name()));
        }
        for (BuildingCard b : p.getTribe().getBuildingCards()) {
            tribe.add(new TribeCardRef("BUILDING", b.getName(), null));
        }
        return tribe;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /** Returns the game phase name at the time of the snapshot. */
    public String               getPhase()      { return phase; }
    /** Returns the round number at the time of the snapshot. */
    public int                  getRound()                    { return round; }
    /** Returns the era name at the time of the snapshot. */
    public String               getEra()                      { return era; }
    /** Returns the id of the player whose turn it is to place, or {@code null}. */
    public String               getCurrentPlacementPlayerId() { return currentPlacementPlayerId; }
    /** Returns the id of the player whose turn it is to execute, or {@code null}. */
    public String               getCurrentExecutionPlayerId() { return currentExecutionPlayerId; }
    /** Returns the id of the player awaiting an extra top-row pick (B020), or {@code null}. */
    public String               getExtraPickPendingId()       { return extraPickPendingId; }
    /** Returns the list of slot snapshots for the top row. */
    public List<SlotSnapshot>   getTopRow()                   { return topRow; }
    /** Returns the list of slot snapshots for the bottom row. */
    public List<SlotSnapshot>   getBottomRow()                { return bottomRow; }
    /** Returns the list of tile snapshots for the offer track. */
    public List<TileSnapshot>   getOfferTrack()               { return offerTrack; }
    /** Returns the list of player snapshots. */
    public List<PlayerSnapshot> getPlayers()                  { return players; }

    // ── Nested DTOs ───────────────────────────────────────────────────────────

    /** One card slot in a visible row. */
    public static class SlotSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String kind;  // EMPTY | CHARACTER | BUILDING | EVENT | FINAL_EVENT
        private final String name;  // card name, null when EMPTY

        private SlotSnapshot(String kind, String name) {
            this.kind = kind;
            this.name = name;
        }

        static SlotSnapshot from(Card card) {
            if (card == null)                   return new SlotSnapshot("EMPTY",       null);
            if (card instanceof FinalEventCard) return new SlotSnapshot("FINAL_EVENT", card.getName());
            if (card instanceof CharacterCard)  return new SlotSnapshot("CHARACTER",   card.getName());
            if (card instanceof BuildingCard)   return new SlotSnapshot("BUILDING",    card.getName());
            if (card instanceof EventCard)      return new SlotSnapshot("EVENT",       card.getName());
            return new SlotSnapshot("UNKNOWN", card.getName());
        }

        /** Returns the slot kind (EMPTY, CHARACTER, BUILDING, EVENT, or FINAL_EVENT). */
        public String getKind() { return kind; }
        /** Returns the card name, or {@code null} if the slot is empty. */
        public String getName() { return name; }

        @Override public String toString() {
            return "EMPTY".equals(kind) ? "EMPTY" : kind + "#" + name;
        }
    }

    /** One tile on the offer track. */
    public static class TileSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String tileId;    // OfferTileId enum name (e.g. "A")
        private final String occupant;  // player id or "free"

        /**
         * Creates a tile snapshot.
         *
         * @param tileId   the tile identifier (e.g. {@code "A"})
         * @param occupant the player id occupying this tile, or {@code "free"}
         */
        public TileSnapshot(String tileId, String occupant) {
            this.tileId   = tileId;
            this.occupant = occupant;
        }

        /** Returns the tile identifier string. */
        public String getTileId()   { return tileId; }
        /** Returns the player id of the occupant, or {@code "free"} if unoccupied. */
        public String getOccupant() { return occupant; }

        @Override public String toString() { return tileId + ":" + occupant; }
    }

    /** Per-player state snapshot. */
    public static class PlayerSnapshot implements Serializable {
        private static final long serialVersionUID = 2L;

        private final String id;
        private final int    food;
        private final int    prestigePoints;
        private final String selectedTile;  // tile id or "-"
        private final String totemColor;    // TotemColor enum name, e.g. "RED"
        private final List<TribeCardRef> tribe;  // acquired character + building cards (public info)

        /**
         * Creates a player snapshot.
         *
         * @param id             the player's identifier
         * @param food           current food supply
         * @param prestigePoints current prestige points
         * @param selectedTile   the id of the selected offer tile, or {@code "-"} if none
         * @param totemColor     the totem colour name
         */
        public PlayerSnapshot(String id, int food, int prestigePoints, String selectedTile,
                              String totemColor, List<TribeCardRef> tribe) {
            this.id             = id;
            this.food           = food;
            this.prestigePoints = prestigePoints;
            this.selectedTile   = selectedTile;
            this.totemColor     = totemColor;
            this.tribe          = tribe == null ? new ArrayList<>() : tribe;
        }

        /** Returns the player's identifier. */
        public String getId()             { return id; }
        /** Returns the player's current food supply. */
        public int    getFood()           { return food; }
        /** Returns the player's current prestige points. */
        public int    getPrestigePoints() { return prestigePoints; }
        /** Returns the selected offer tile id, or {@code "-"} if no tile is selected. */
        public String getSelectedTile()   { return selectedTile; }
        /** Returns the player's totem colour name. */
        public String getTotemColor()     { return totemColor; }
        public List<TribeCardRef> getTribe() { return tribe; }

        @Override public String toString() {
            return id + " food=" + food + " pp=" + prestigePoints + " tile=" + selectedTile
                    + " tribe=" + tribe.size();
        }
    }

    /**
     * One acquired card in a player's tribe. {@code kind} is CHARACTER or BUILDING;
     * {@code type} is the {@link mesos.enums.CharacterType} name for characters,
     * {@code null} for buildings. {@code name} maps to the card art via CardArtManifest.
     */
    public static class TribeCardRef implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String kind;   // "CHARACTER" | "BUILDING"
        private final String name;   // card name (-> /img/card/front-NNN.png)
        private final String type;   // CharacterType name for characters; null for buildings

        public TribeCardRef(String kind, String name, String type) {
            this.kind = kind;
            this.name = name;
            this.type = type;
        }

        public String getKind() { return kind; }
        public String getName() { return name; }
        public String getType() { return type; }

        @Override public String toString() {
            return kind + "#" + name + (type != null ? "(" + type + ")" : "");
        }
    }
}
