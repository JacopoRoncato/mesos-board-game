package mesos.cards.events;

import java.util.List;
import java.util.Set;
import mesos.cards.TribeDeckCard;
import mesos.enums.EventType;
import mesos.enums.Era;
import mesos.enums.PlayerCount;
import mesos.player.Player;

/** Abstract base class for event cards drawn from the tribe deck. */
public abstract class EventCard extends TribeDeckCard {
    private static final long serialVersionUID = 1L;
    private final EventType eventType;

    protected EventCard(String name, Era era, Set<PlayerCount> allowedPlayerCounts, EventType eventType) {
        super(name, era, allowedPlayerCounts);
        this.eventType = eventType;
    }

    /**
     * Resolves this event for all players.
     *
     * @param players all active players, in turn order
     */
    public abstract void resolveEvent(List<Player> players);

    /** Returns the type of event represented by this card. */
    public EventType getEventType() {
        return eventType;
    }
}
