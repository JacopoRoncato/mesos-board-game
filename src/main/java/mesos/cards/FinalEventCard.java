package mesos.cards;

import java.util.EnumSet;
import mesos.enums.Era;
import mesos.enums.PlayerCount;

/**
 * Marker card placed at the bottom of the Tribe deck.
 * Not an EventCard — cannot be resolved as an event.
 * Signals that the deck is effectively exhausted.
 */
public class FinalEventCard extends TribeDeckCard {
    private static final long serialVersionUID = 1L;
    public FinalEventCard(String name) {
        super(name, Era.III, EnumSet.allOf(PlayerCount.class));
    }
}
