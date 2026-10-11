package mesos.board;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import mesos.cards.BuildingCard;
import mesos.cards.Card;
import mesos.cards.FinalEventCard;
import mesos.cards.TribeDeckCard;
import mesos.cards.events.EventCard;
import mesos.enums.Era;
import mesos.enums.PlayerCount;
import mesos.enums.VisibleRow;
import mesos.offer.CardPick;
import mesos.offer.OfferTrack;
import mesos.order.TurnOrderTile;

/**
 * Aggregate root for the shared game-area state: the two visible rows, the
 * Offer Track, the Turn Order Tile, the tribe deck and the per-era building
 * decks. Deck construction is delegated to {@link TribeDeckFactory} and
 * {@link BuildingDeckFactory}; this class only orchestrates them.
 */
public class Board implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int CARDS_PER_ERA_TYPE = 5;

    private final List<TribeDeckCard> tribeDeck;
    private final Map<Era, List<BuildingCard> > buildingDecks;
    private final VisibleRowState topRow;
    private final VisibleRowState bottomRow;
    private final OfferTrack offerTrack;
    private final TurnOrderTile turnOrderTile;

    /** Creates a new empty board. All decks and rows are initialised empty. */
    public Board() {
        this.tribeDeck = new ArrayList<>();
        this.buildingDecks = new EnumMap<>(Era.class);
        this.topRow = new VisibleRowState();
        this.bottomRow = new VisibleRowState();
        this.offerTrack = new OfferTrack();
        this.turnOrderTile = new TurnOrderTile();
    }


    /**
     * Builds and shuffles the tribe deck for the given player count,
     * appending two {@link FinalEventCard}s at the bottom.
     */
    public void buildTribeDeck(PlayerCount playerCount) {
        tribeDeck.clear();
        for (Era era : Era.values()) {
            List<TribeDeckCard> slice =
                    TribeDeckFactory.createEraSlice(era, playerCount, CARDS_PER_ERA_TYPE);
            Collections.shuffle(slice);
            tribeDeck.addAll(slice);
        }
        // 2 Final Event cards at the bottom (drawn last)
        tribeDeck.add(new FinalEventCard("FinalEvent-1"));
        tribeDeck.add(new FinalEventCard("FinalEvent-2"));
    }


    /** Builds and shuffles the per-era building decks, trimmed to the count for the player count. */
    public void buildBuildingDecks(PlayerCount playerCount) {
        buildingDecks.clear();
        Map<Era, Integer> counts = buildingCountsFor(playerCount);
        for (Era era : Era.values()) {
            List<BuildingCard> pool = BuildingDeckFactory.createPool(era);
            Collections.shuffle(pool);
            int count = Math.min(counts.get(era), pool.size());
            buildingDecks.put(era, new ArrayList<>(pool.subList(0, count)));
        }
    }

    /** Configures the offer track tile layout for the given player count. */
    public void configureOfferTrack(PlayerCount playerCount) {
        offerTrack.configureForPlayerCount(playerCount);
    }

    /** Configures the turn-order tile spaces for the given player count. */
    public void configureTurnOrderTile(PlayerCount playerCount) {
        turnOrderTile.configureForPlayerCount(playerCount);
    }

    /** Reveals the initial top and bottom rows, drawing from the tribe deck. */
    public void revealInitialRows(PlayerCount playerCount) {
        int n = playerCount.toInt();
        int bottomNeeded = n + 1;
        int topNeeded = n + 4;
        while (bottomRow.countNonBuildingCards() < bottomNeeded && !tribeDeck.isEmpty()) {
            TribeDeckCard card = tribeDeck.remove(0);
            if (card instanceof EventCard) {
                topRow.appendCard(card);
            } else {
                bottomRow.appendCard(card);
            }
        }
        while (topRow.countNonBuildingCards() < topNeeded && !tribeDeck.isEmpty()) {
            topRow.appendCard(tribeDeck.remove(0));
        }
    }


    /** Reveals the Era I building cards into the top row. */
    public void revealInitialBuildings(PlayerCount playerCount) {
        List<BuildingCard> eraOneDeck = buildingDecks.getOrDefault(Era.I, List.of());
        for (BuildingCard buildingCard : new ArrayList<>(eraOneDeck)) {
            topRow.appendCard(buildingCard);
        }
        eraOneDeck.clear();
    }

    /** Returns the visible row state for the given row identifier. */
    public VisibleRowState getVisibleRow(VisibleRow row) {
        return row == VisibleRow.TOP ? topRow : bottomRow;
    }

    /** Returns the offer track. */
    public OfferTrack getOfferTrack() {
        return offerTrack;
    }

    /** Returns the turn-order tile. */
    public TurnOrderTile getTurnOrderTile() {
        return turnOrderTile;
    }

    /**
     * Removes and returns the card at the given pick position.
     *
     * @param pick the row and slot index of the card to take
     * @return the card that was taken, or {@code null} if the slot was empty
     */
    public Card takeCard(CardPick pick) {
        VisibleRowState row = getVisibleRow(pick.getRow());
        return row.removeCardAt(pick.getSlotIndex());
    }

    /** Removes all character and event cards from the bottom row. */
    public void discardBottomCharactersAndEvents() {
        bottomRow.removeCharactersAndEvents();
    }

    /** Moves all character and event cards from the top row to the bottom row. */
    public void moveTopCharactersAndEventsToBottom() {
        for (Card card : topRow.extractCharactersAndEvents()) {
            bottomRow.appendCard(card);
        }
    }

    /**
     * Performs the end-of-round row refill: discards bottom non-building cards,
     * slides top non-building cards down, then draws from the tribe deck to refill the top row.
     *
     * @param playerCount the current player count (determines required top-row size)
     * @param currentEra  the current era (used to detect era transitions)
     * @return a {@link RowRefillResult} describing any era transition and whether the deck is exhausted
     */
    public RowRefillResult refillTopRow(PlayerCount playerCount, Era currentEra) {
        discardBottomCharactersAndEvents();
        moveTopCharactersAndEventsToBottom();

        int requiredTopCards = playerCount.toInt() + 4;
        Era visibleEra = currentEra;
        boolean eraTransitionTriggered = false;
        Era newEra = null;

        boolean deckExhausted = false;
        while (topRow.countNonBuildingCards() < requiredTopCards && !tribeDeck.isEmpty()) {
            TribeDeckCard drawn = tribeDeck.remove(0);
            if (drawn instanceof FinalEventCard) {
                // Final Event marker reached — deck is effectively exhausted
                deckExhausted = true;
                break;
            }
            topRow.appendCard(drawn);
            if (!eraTransitionTriggered && drawn.getEra().ordinal() > visibleEra.ordinal()) {
                eraTransitionTriggered = true;
                newEra = drawn.getEra();
            }
        }

        if (!deckExhausted) {
            deckExhausted = tribeDeck.isEmpty() && topRow.countNonBuildingCards() < requiredTopCards;
        }
        return new RowRefillResult(eraTransitionTriggered, newEra, deckExhausted);
    }

    /**
     * Applies the building deck transition when a new era is revealed:
     * moves current top buildings to the bottom row and reveals the new era's buildings.
     *
     * @param newEra the era that was just entered
     */
    public void applyEraTransition(Era newEra) {
        if (newEra == Era.III) {
            discardBottomBuildingsForEraIII();
        }
        moveTopBuildingsToBottom();
        revealBuildingsForEra(newEra);
    }

    /** Removes all building cards from the bottom row (called when Era III begins). */
    public void discardBottomBuildingsForEraIII() {
        bottomRow.removeBuildings();
    }

    /** Moves all building cards from the top row to the bottom row. */
    public void moveTopBuildingsToBottom() {
        for (Card card : topRow.extractBuildings()) {
            bottomRow.appendCard(card);
        }
    }

    /** Reveals all building cards for the given era into the top row. */
    public void revealBuildingsForEra(Era newEra) {
        List<BuildingCard> deck = buildingDecks.getOrDefault(newEra, List.of());
        for (BuildingCard building : new ArrayList<>(deck)) {
            topRow.appendCard(building);
        }
        deck.clear();
    }

    /** Returns the list of event cards currently in the bottom row (the active round events). */
    public List<EventCard> getActiveRoundEvents() {
        return extractEvents(bottomRow);
    }

    /** Returns all event cards visible in both the top and bottom rows. */
    public List<EventCard> getAllVisibleEvents() {
        List<EventCard> events = new ArrayList<>();
        events.addAll(extractEvents(topRow));
        events.addAll(extractEvents(bottomRow));
        return events;
    }

    /**
     * Returns the given event list sorted in resolution order
     * (hunting → shamanic ritual → cave paintings → sustenance), then by era.
     */
    public List<EventCard> sortEventsForResolution(List<EventCard> events) {
        List<EventCard> ordered = new ArrayList<>(events);
        ordered.sort(Comparator.comparingInt(Board::eventPriority)
                .thenComparing(e -> e.getEra().ordinal()));
        return ordered;
    }

    // ---- helpers ----

    private static List<EventCard> extractEvents(VisibleRowState row) {
        return row.getSlots().stream()
                .map(RowSlot::getCard)
                .filter(EventCard.class::isInstance)
                .map(EventCard.class::cast)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static int eventPriority(EventCard eventCard) {
        return switch (eventCard.getEventType()) {
            case HUNTING         -> 1;
            case SHAMANIC_RITUAL -> 2;
            case CAVE_PAINTINGS  -> 3;
            case SUSTENANCE      -> 99;
        };
    }

    private static Map<Era, Integer> buildingCountsFor(PlayerCount playerCount) {
        Map<Era, Integer> counts = new EnumMap<>(Era.class);
        switch (playerCount) {
            case TWO -> {
                counts.put(Era.I, 1);
                counts.put(Era.II, 2);
                counts.put(Era.III, 3);
            }
            case THREE -> {
                counts.put(Era.I, 2);
                counts.put(Era.II, 2);
                counts.put(Era.III, 4);
            }
            case FOUR -> {
                counts.put(Era.I, 2);
                counts.put(Era.II, 3);
                counts.put(Era.III, 4);
            }
            case FIVE -> {
                counts.put(Era.I, 2);
                counts.put(Era.II, 3);
                counts.put(Era.III, 5);
            }
        }
        return counts;
    }
}
