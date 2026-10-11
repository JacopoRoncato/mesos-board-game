package mesos.order;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mesos.enums.PlayerCount;
import mesos.player.Player;
import mesos.player.Totem;

/**
 * The turn-order tile: an ordered sequence of {@link TurnOrderSpace}s on which
 * players place their totems during the action phase to claim their place in next
 * round's turn order. The last space is a penalty space.
 */
public class TurnOrderTile implements Serializable {
    private static final long serialVersionUID = 1L;
    private final List<TurnOrderSpace> spaces;
    private final Map<Totem, Player> playerByTotem;

    /** Creates an empty tile. Call {@link #configureForPlayerCount} before use. */
    public TurnOrderTile() {
        this.spaces = new ArrayList<>();
        this.playerByTotem = new LinkedHashMap<>();
    }


    /**
     * Configures the spaces and their food bonuses for the given player count.
     * Food bonuses per space (last element = penalty space, bonus 0).
     * Values from rulebook: 2p → +1,penalty; 3p → +2,0,penalty;
     * 4p → +2,+1,0,penalty; 5p → +3,+1,0,0,penalty.
     */
    public void configureForPlayerCount(PlayerCount playerCount) {
        spaces.clear();
        playerByTotem.clear();
        int[] bonuses = switch (playerCount) {
            case TWO   -> new int[]{1, 0};
            case THREE -> new int[]{2, 0, 0};
            case FOUR  -> new int[]{2, 1, 0, 0};
            case FIVE  -> new int[]{3, 1, 0, 0, 0};
        };
        for (int i = 0; i < bonuses.length; i++) {
            boolean last = (i == bonuses.length - 1);
            spaces.add(new TurnOrderSpace(i, bonuses[i], last));
        }
    }


    /**
     * Places the given totem on the first free space and records the owning player.
     *
     * @return the space the totem was placed on, or {@code null} if all spaces are full
     */
    public TurnOrderSpace addTotem(Totem totem, Player player) {
        TurnOrderSpace firstFreeSpace = getFirstFreeSpace();
        if (firstFreeSpace != null) {
            firstFreeSpace.assignTotem(totem);
            playerByTotem.put(totem, player);
        }
        return firstFreeSpace;
    }

    /**
     * Returns the players in turn order for the next round, in the order their
     * totems were placed on the spaces.
     */
    public List<Player> determineNewOrder() {
        List<Player> ordered = new ArrayList<>();
        for (TurnOrderSpace space : spaces) {
            if (space.getOccupiedBy() != null) {
                Player player = playerByTotem.get(space.getOccupiedBy());
                if (player != null) {
                    ordered.add(player);
                }
            }
        }
        return ordered;
    }

    /**
     * Applies the food bonus or penalty associated with a turn-order space.
     * The last space deducts 1 food (or 2 PP if the player cannot pay).
     */
    public void applySpaceEffect(TurnOrderSpace space, Player player) {
        if (space == null || player == null) {
            return;
        }
        if (space.getFoodBonus() > 0) {
            player.gainFood(space.getFoodBonus());
        }
        if (space.isLastSpace() && !player.payFood(1)) {
            player.removePrestige(2);
        }
    }

    /** Returns the first space that currently has no totem, or {@code null} if all are occupied. */
    public TurnOrderSpace getFirstFreeSpace() {
        return spaces.stream().filter(TurnOrderSpace::isFree).findFirst().orElse(null);
    }

    /** Returns a defensive copy of all spaces on this tile. */
    public List<TurnOrderSpace> getSpaces() {
        return new ArrayList<>(spaces);
    }

    /** Removes all totems from all spaces and clears the player mapping. */
    public void clearSpaces() {
        spaces.forEach(TurnOrderSpace::clear);
        playerByTotem.clear();
    }

    /**
     * Removes a single totem from whichever space it currently occupies.
     * Used during the placement phase as players move their totems off
     * the Turn Order Tile and onto Offer Tiles (rules §6).
     *
     * @param totem the totem to remove
     * @return {@code true} if the totem was found and removed
     */
    public boolean clearTotem(Totem totem) {
        if (totem == null) return false;
        boolean found = false;
        for (TurnOrderSpace space : spaces) {
            if (totem.equals(space.getOccupiedBy())) {
                space.clear();
                found = true;
            }
        }
        if (found) {
            playerByTotem.remove(totem);
        }
        return found;
    }
}
