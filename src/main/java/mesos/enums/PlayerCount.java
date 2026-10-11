package mesos.enums;

/** Supported player counts for a game session (2 to 5). */
public enum PlayerCount {
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5);

    private final int value;

    PlayerCount(int value) {
        this.value = value;
    }

    /** Returns the numeric value of this player count. */
    public int toInt() {
        return value;
    }

    /**
     * Returns the {@code PlayerCount} constant that corresponds to the given integer.
     *
     * @param n the number of players (must be 2–5)
     * @throws IllegalArgumentException if {@code n} is not a supported player count
     */
    public static PlayerCount from(int n) {
        return switch (n) {
            case 2 -> TWO;
            case 3 -> THREE;
            case 4 -> FOUR;
            case 5 -> FIVE;
            default -> throw new IllegalArgumentException("Unsupported player count: " + n);
        };
    }
}
