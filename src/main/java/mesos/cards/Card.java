package mesos.cards;

import java.io.Serializable;
import mesos.enums.Era;

/** Base class for all cards in the game. Holds the card's name and era. */
public abstract class Card implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String name;
    private final Era era;

    protected Card(String name, Era era) {
        this.name = name;
        this.era = era;
    }

    /** Returns the display name of this card. */
    public String getName() {
        return name;
    }

    /** Returns the era this card belongs to. */
    public Era getEra() {
        return era;
    }
}
