package mesos.board;

import java.io.Serializable;
import mesos.cards.BuildingCard;
import mesos.cards.Card;
import mesos.cards.FinalEventCard;
import mesos.cards.characters.CharacterCard;
import mesos.cards.events.EventCard;

/** A single slot in a visible row. Holds one card (or is empty). */
public class RowSlot implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int index;
    private Card card;

    /**
     * Creates a slot at the given index, pre-filled with the given card.
     *
     * @param index the position of this slot within its row
     * @param card  the card initially in this slot, or {@code null} for an empty slot
     */
    public RowSlot(int index, Card card) {
        this.index = index;
        this.card = card;
    }

    /** Returns {@code true} if this slot holds no card. */
    public boolean isEmpty() {
        return card == null;
    }

    /** Returns {@code true} if this slot holds a character card. */
    public boolean containsCharacter() {
        return card instanceof CharacterCard;
    }

    /** Returns {@code true} if this slot holds an event card. */
    public boolean containsEvent() {
        return card instanceof EventCard;
    }

    /** Returns {@code true} if this slot holds a building card. */
    public boolean containsBuilding() {
        return card instanceof BuildingCard;
    }

    /** Returns {@code true} if this slot holds a final-event card. */
    public boolean containsFinalEvent() {
        return card instanceof FinalEventCard;
    }

    /**
     * Returns {@code true} if this slot can be selected by a player
     * (non-empty and not an event or final-event card).
     */
    public boolean isSelectable() {
        return !isEmpty() && !containsEvent() && !containsFinalEvent();
    }

    /** Returns the position of this slot within its row. */
    public int getIndex() {
        return index;
    }

    /** Returns the card in this slot, or {@code null} if empty. */
    public Card getCard() {
        return card;
    }

    /** Replaces the card in this slot. */
    public void setCard(Card card) {
        this.card = card;
    }

    /** Removes the card from this slot, leaving it empty. */
    public void clearCard() {
        this.card = null;
    }
}
