package mesos.cards.characters;

import java.util.Set;
import mesos.enums.CharacterType;
import mesos.enums.Era;
import mesos.enums.PlayerCount;
import mesos.player.Player;

/**
 * A Hunter character card. If this card shows an immediate food icon,
 * the player gains food equal to their current hunter count upon purchase.
 */
public class Hunter extends CharacterCard {
    private static final long serialVersionUID = 1L;
    private final boolean hasImmediateFoodIcon;

    /**
     * Creates a Hunter card.
     *
     * @param hasImmediateFoodIcon {@code true} if this card triggers immediate food gain on purchase
     */
    public Hunter(String name, Era era, Set<PlayerCount> allowedPlayerCounts, boolean hasImmediateFoodIcon) {
        super(name, era, allowedPlayerCounts);
        this.hasImmediateFoodIcon = hasImmediateFoodIcon;
    }

    @Override
    public void onPurchase(Player player) {
        if (hasImmediateFoodIcon) {
            int hunterCount = player.getTribe().countCharacters(mesos.enums.CharacterType.HUNTER);
            player.gainFood(hunterCount);
        }
    }

    @Override
    public CharacterType getType() {
        return CharacterType.HUNTER;
    }

    /** Returns {@code true} if this hunter card has an immediate food icon. */
    public boolean hasImmediateFoodIcon() {
        return hasImmediateFoodIcon;
    }
}
