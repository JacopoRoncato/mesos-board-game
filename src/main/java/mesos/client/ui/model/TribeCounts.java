package mesos.client.ui.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import mesos.client.ui.model.GameSnapshot.TribeCard;
import mesos.client.ui.widget.CardComponent;
import mesos.enums.CharacterType;

/**
 * Pure aggregation of a player's tribe into per-type character counts and a
 * building count. No Swing dependency — testable in isolation.
 *
 * <p>This is the data behind the always-visible counts bar; it does not depend
 * on the (removable) card-expansion module.
 */
public final class TribeCounts {

    private final Map<CharacterType, Integer> characters;
    private final int buildings;

    private TribeCounts(Map<CharacterType, Integer> characters, int buildings) {
        this.characters = characters;
        this.buildings = buildings;
    }

    /** Builds counts from a tribe list. {@code null}/empty yields all-zero counts. */
    public static TribeCounts of(List<TribeCard> tribe) {
        Map<CharacterType, Integer> chars = new EnumMap<>(CharacterType.class);
        for (CharacterType t : CharacterType.values()) chars.put(t, 0);
        int buildings = 0;
        if (tribe != null) {
            for (TribeCard c : tribe) {
                if (c == null) continue;
                if (c.kind == CardComponent.Kind.BUILDING) {
                    buildings++;
                } else if (c.type != null) {
                    chars.merge(c.type, 1, Integer::sum);
                }
            }
        }
        return new TribeCounts(chars, buildings);
    }

    public int characters(CharacterType type) { return characters.getOrDefault(type, 0); }

    public int buildings() { return buildings; }

    public int totalCharacters() {
        int sum = 0;
        for (int n : characters.values()) sum += n;
        return sum;
    }
}
