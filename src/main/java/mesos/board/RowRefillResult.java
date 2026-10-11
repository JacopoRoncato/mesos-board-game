package mesos.board;

import mesos.enums.Era;

/**
 * Value object returned by {@link Board#refillTopRow} that describes what
 * happened during the row refill: whether an era transition was triggered,
 * which era was entered, and whether the tribe deck is now exhausted.
 */
public class RowRefillResult {
    private final boolean eraTransitionTriggered;
    private final Era newEra;
    private final boolean deckExhausted;

    /**
     * Creates a row-refill result.
     *
     * @param eraTransitionTriggered {@code true} if a card from a higher era was drawn
     * @param newEra                 the era entered, or {@code null} if no transition occurred
     * @param deckExhausted          {@code true} if the tribe deck is now exhausted
     */
    public RowRefillResult(boolean eraTransitionTriggered, Era newEra, boolean deckExhausted) {
        this.eraTransitionTriggered = eraTransitionTriggered;
        this.newEra = newEra;
        this.deckExhausted = deckExhausted;
    }

    /** Returns {@code true} if a new era was entered during this refill. */
    public boolean isEraTransitionTriggered() {
        return eraTransitionTriggered;
    }

    /** Returns the new era that was entered, or {@code null} if no transition occurred. */
    public Era getNewEra() {
        return newEra;
    }

    /** Returns {@code true} if the tribe deck is exhausted after this refill. */
    public boolean isDeckExhausted() {
        return deckExhausted;
    }
}
