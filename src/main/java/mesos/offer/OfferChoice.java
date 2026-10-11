package mesos.offer;

import java.util.ArrayList;
import java.util.List;
import mesos.enums.VisibleRow;

/**
 * The card picks a player has selected for a given {@link OfferTile}.
 * Picks are ordered: the player adds them in the order they wish to execute them.
 */
public class OfferChoice {
    private final OfferTile offerTile;
    private final List<CardPick> orderedPicks;

    /**
     * Creates an empty choice for the given tile.
     *
     * @param offerTile the tile this choice is associated with
     */
    public OfferChoice(OfferTile offerTile) {
        this.offerTile = offerTile;
        this.orderedPicks = new ArrayList<>();
    }

    /**
     * Appends a card pick to this choice.
     *
     * @param pick the pick to add
     */
    public void addPick(CardPick pick) {
        orderedPicks.add(pick);
    }

    /** Returns the number of picks targeting the given row. */
    public int countPicksFromRow(VisibleRow row) {
        return (int) orderedPicks.stream().filter(pick -> pick.getRow() == row).count();
    }

    /** Returns a defensive copy of the ordered list of picks. */
    public List<CardPick> getOrderedPicks() {
        return new ArrayList<>(orderedPicks);
    }

    /** Returns {@code true} if this choice contains no card picks. */
    public boolean isEmpty() {
        return orderedPicks.isEmpty();
    }

    /** Returns the tile this choice is associated with. */
    public OfferTile getOfferTile() {
        return offerTile;
    }
}
