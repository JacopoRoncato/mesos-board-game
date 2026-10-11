package mesos.client.ui.model;

/**
 * Per-tile pick budget remaining during ACTION_MINE.
 * {@code top} = picks left from the top visible row.
 * {@code bottom} = picks left from the bottom visible row.
 *
 * <p>Constructed by {@link PhaseController} from the tile chosen during
 * placement (its tileTop / tileBottom budget), then decremented each time
 * the player clicks an eligible card. When {@link #isDone()} returns
 * true, the client sends the assembled {@code EXECUTE} command.
 */
public record PicksRemaining(int top, int bottom) {

    public PicksRemaining {
        if (top < 0 || bottom < 0) {
            throw new IllegalArgumentException("budgets must be non-negative: top=" + top + " bottom=" + bottom);
        }
    }

    public boolean isDone() {
        return top == 0 && bottom == 0;
    }

    public int total() {
        return top + bottom;
    }

    public PicksRemaining decTop() {
        if (top == 0) throw new IllegalStateException("no top picks remaining");
        return new PicksRemaining(top - 1, bottom);
    }

    public PicksRemaining decBottom() {
        if (bottom == 0) throw new IllegalStateException("no bottom picks remaining");
        return new PicksRemaining(top, bottom - 1);
    }
}
