package mesos.player;

import java.io.Serializable;
import java.util.Objects;
import mesos.enums.TotemColor;

/** A player's totem, identified by its colour. Two totems are equal iff they share the same colour. */
public class Totem implements Serializable {
    private static final long serialVersionUID = 1L;
    private final TotemColor color;

    /**
     * Creates a totem with the given colour.
     *
     * @param color the colour of this totem
     */
    public Totem(TotemColor color) {
        this.color = color;
    }

    /** Returns the colour of this totem. */
    public TotemColor getColor() {
        return color;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Totem totem)) {
            return false;
        }
        return color == totem.color;
    }

    @Override
    public int hashCode() {
        return Objects.hash(color);
    }
}
