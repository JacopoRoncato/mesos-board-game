package mesos.board;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import mesos.cards.Card;

/**
 * Holds the ordered list of {@link RowSlot}s that make up a visible row
 * (top or bottom). Grows dynamically as cards are appended.
 */
public class VisibleRowState implements Serializable {
    private static final long serialVersionUID = 1L;
    private final List<RowSlot> slots;

    /** Creates an empty row with no slots. */
    public VisibleRowState() {
        this.slots = new ArrayList<>();
    }

    /**
     * Returns the slot at the given index, or {@code null} if no slot with that index exists.
     *
     * @param index the slot index to look up
     */
    public RowSlot getSlot(int index) {
        return slots.stream().filter(slot -> slot.getIndex() == index).findFirst().orElse(null);
    }

    /** Returns a defensive copy of all slots in this row. */
    public List<RowSlot> getSlots() {
        return new ArrayList<>(slots);
    }

    /** Returns the subset of slots that are currently selectable by a player. */
    public List<RowSlot> getSelectableSlots() {
        return slots.stream().filter(RowSlot::isSelectable).collect(Collectors.toList());
    }

    /**
     * Removes and returns the card at the given slot index, leaving the slot empty.
     *
     * @param index the slot index
     * @return the removed card, or {@code null} if no slot with that index exists or the slot was empty
     */
    public Card removeCardAt(int index) {
        RowSlot slot = getSlot(index);
        if (slot == null) {
            return null;
        }
        Card card = slot.getCard();
        slot.clearCard();
        return card;
    }

    /**
     * Places the given card into the first empty slot, or creates a new slot at the end if all slots are occupied.
     *
     * @param card the card to append; ignored if {@code null}
     */
    public void appendCard(Card card) {
        if (card == null) {
            return;
        }
        for (RowSlot slot : slots) {
            if (slot.isEmpty()) {
                slot.setCard(card);
                return;
            }
        }
        int nextIndex = slots.size();
        slots.add(new RowSlot(nextIndex, card));
    }

    /** Clears all character and event cards from this row in place. */
    public void removeCharactersAndEvents() {
        for (RowSlot slot : slots) {
            if (slot.containsCharacter() || slot.containsEvent()) {
                slot.clearCard();
            }
        }
    }

    /** Removes all character and event cards from this row and returns them. */
    public List<Card> extractCharactersAndEvents() {
        List<Card> result = new ArrayList<>();
        for (RowSlot slot : slots) {
            if (slot.containsCharacter() || slot.containsEvent()) {
                result.add(slot.getCard());
                slot.clearCard();
            }
        }
        return result;
    }

    /** Clears all building cards from this row in place. */
    public void removeBuildings() {
        for (RowSlot slot : slots) {
            if (slot.containsBuilding()) {
                slot.clearCard();
            }
        }
    }

    /** Removes all building cards from this row and returns them. */
    public List<Card> extractBuildings() {
        List<Card> result = new ArrayList<>();
        for (RowSlot slot : slots) {
            if (slot.containsBuilding()) {
                result.add(slot.getCard());
                slot.clearCard();
            }
        }
        return result;
    }

    /** Returns the number of non-building, non-empty cards in this row. */
    public int countNonBuildingCards() {
        int count = 0;
        for (RowSlot slot : slots) {
            if (!slot.isEmpty() && !slot.containsBuilding()) {
                count++;
            }
        }
        return count;
    }
}
