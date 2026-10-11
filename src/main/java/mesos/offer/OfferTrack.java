package mesos.offer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import mesos.enums.OfferTileId;
import mesos.enums.PlayerCount;
import mesos.player.Player;
import mesos.player.Totem;

/** The offer track: the ordered collection of {@link OfferTile}s that players place their totem on. */
public class OfferTrack implements Serializable {
    private static final long serialVersionUID = 1L;
    private final List<OfferTile> tiles;

    /** Creates an empty offer track. Call {@link #configureForPlayerCount} before use. */
    public OfferTrack() {
        this.tiles = new ArrayList<>();
    }

    /** Populates the track with the tiles that are active for the given player count. */
    public void configureForPlayerCount(PlayerCount playerCount) {
        tiles.clear();
        addTile(playerCount, OfferTileId.A, 0, 0, 3, PlayerCount.FIVE);
        addTile(playerCount, OfferTileId.B, 0, 1, 0, PlayerCount.TWO, PlayerCount.THREE, PlayerCount.FOUR, PlayerCount.FIVE);
        addTile(playerCount, OfferTileId.C, 1, 0, 0, PlayerCount.TWO, PlayerCount.THREE, PlayerCount.FOUR, PlayerCount.FIVE);
        addTile(playerCount, OfferTileId.D, 0, 2, 0, PlayerCount.THREE, PlayerCount.FOUR, PlayerCount.FIVE);
        addTile(playerCount, OfferTileId.E, 1, 1, 0, PlayerCount.TWO, PlayerCount.THREE, PlayerCount.FOUR, PlayerCount.FIVE);
        addTile(playerCount, OfferTileId.F, 2, 0, 0, PlayerCount.TWO, PlayerCount.THREE, PlayerCount.FOUR, PlayerCount.FIVE);
        addTile(playerCount, OfferTileId.G, 2, 1, 0, PlayerCount.FOUR, PlayerCount.FIVE);
    }

    private void addTile(PlayerCount current, OfferTileId id, int top, int bottom, int food, PlayerCount... allowed) {
        OfferTile tile = new OfferTile(id);
        tile.setCardsFromTop(top);
        tile.setCardsFromBottom(bottom);
        tile.setDirectFoodBonus(food);
        tile.setAllowedPlayerCounts(allowed);
        if (tile.getAllowedPlayerCounts().contains(current)) {
            tiles.add(tile);
        }
    }

    /**
     * Places the player's totem on the given tile. Returns true on success,
     * false if the tile is null, not on this track, or already occupied.
     *
     * If the player's totem is already on another tile of this track
     * (mis-behaving controller calling PLACE twice in a row), the previous
     * tile is cleared first — a totem can only be in one place.
     */
    public boolean placeTotem(Player player, OfferTile tile) {
        if (player == null || tile == null || !tile.isAvailable()) {
            return false;
        }
        if (!tiles.contains(tile)) {
            return false;
        }
        Totem totem = player.getTotem();
        for (OfferTile other : tiles) {
            if (other != tile && totem.equals(other.getOccupiedBy())) {
                other.setOccupiedBy(null);
            }
        }
        tile.setOccupiedBy(totem);
        return true;
    }

    /** Returns all tiles sorted by their identifier (A → G), which is the resolution order. */
    public List<OfferTile> getTilesInResolutionOrder() {
        return tiles.stream().sorted(Comparator.comparing(OfferTile::getId)).toList();
    }

    /** Returns a defensive copy of all tiles on this track (in configuration order). */
    public List<OfferTile> getTiles() {
        return new ArrayList<>(tiles);
    }
}
