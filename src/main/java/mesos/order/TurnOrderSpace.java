package mesos.order;

import java.io.Serializable;
import mesos.player.Totem;

/**
 * A single space on the {@link TurnOrderTile}. Holds a food bonus
 * and a flag indicating whether this is the last (penalty) space.
 */
public class TurnOrderSpace implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int position;
    private final int foodBonus;
    private final boolean lastSpace;
    private Totem occupiedBy;

    /**
     * Creates a turn-order space.
     *
     * @param position  zero-based position on the tile (0 = first in turn order)
     * @param foodBonus food granted when a totem is placed here (0 for no bonus)
     * @param lastSpace {@code true} if this is the penalty space (last position)
     */
    public TurnOrderSpace(int position, int foodBonus, boolean lastSpace) {
        this.position = position;
        this.foodBonus = foodBonus;
        this.lastSpace = lastSpace;
    }

    /** Returns {@code true} if no totem is currently on this space. */
    public boolean isFree() {
        return occupiedBy == null;
    }

    /** Places the given totem on this space. */
    public void assignTotem(Totem totem) {
        this.occupiedBy = totem;
    }

    /** Removes the totem from this space. */
    public void clear() {
        this.occupiedBy = null;
    }

    /** Returns the zero-based position of this space on the tile. */
    public int getPosition() {
        return position;
    }

    /** Returns the food bonus granted when a totem is placed here. */
    public int getFoodBonus() {
        return foodBonus;
    }

    /** Returns {@code true} if this is the last (penalty) space on the tile. */
    public boolean isLastSpace() {
        return lastSpace;
    }

    /** Returns the totem currently on this space, or {@code null} if the space is free. */
    public Totem getOccupiedBy() {
        return occupiedBy;
    }
}
