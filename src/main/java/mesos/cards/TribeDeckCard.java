package mesos.cards;

import java.util.EnumSet;
import java.util.Set;
import mesos.enums.Era;
import mesos.enums.PlayerCount;

/**
 * Abstract base class for cards that belong to the tribe deck
 * (character cards and event cards).
 * Each card is valid only for a specific set of player counts.
 */
public abstract class TribeDeckCard extends Card {
    private static final long serialVersionUID = 1L;
    private final Set<PlayerCount> allowedPlayerCounts;

    /**
     * Creates a tribe-deck card.
     *
     * @param name                display name
     * @param era                 the era this card belongs to
     * @param allowedPlayerCounts the player counts for which this card is included;
     *                            {@code null} or empty means all player counts
     */
    protected TribeDeckCard(String name, Era era, Set<PlayerCount> allowedPlayerCounts) {
        super(name, era);
        this.allowedPlayerCounts = allowedPlayerCounts == null || allowedPlayerCounts.isEmpty()
                ? EnumSet.allOf(PlayerCount.class)
                : EnumSet.copyOf(allowedPlayerCounts);
    }

    /** Returns a copy of the set of player counts for which this card is included in the deck. */
    public Set<PlayerCount> getAllowedPlayerCounts() {
        return EnumSet.copyOf(allowedPlayerCounts);
    }
}
