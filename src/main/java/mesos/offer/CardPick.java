package mesos.offer;

import mesos.enums.VisibleRow;

/** Identifies a single card slot that a player wishes to take: row (top/bottom) + slot index. */
public class CardPick {
    private final VisibleRow row;
    private final int slotIndex;

    /**
     * Creates a card pick.
     *
     * @param row       the visible row the card is in
     * @param slotIndex the index of the slot within that row
     */
    public CardPick(VisibleRow row, int slotIndex) {
        this.row = row;
        this.slotIndex = slotIndex;
    }

    /** Returns the row this pick targets. */
    public VisibleRow getRow() {
        return row;
    }

    /** Returns the slot index within the row. */
    public int getSlotIndex() {
        return slotIndex;
    }
}
