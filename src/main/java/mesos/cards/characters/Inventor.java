package mesos.cards.characters;

import java.util.Set;
import mesos.enums.CharacterType;
import mesos.enums.Era;
import mesos.enums.InventionIcon;
import mesos.enums.PlayerCount;
import mesos.player.Player;

/** An Inventor character card. Carries one invention icon used for scoring and building bonuses. */
public class Inventor extends CharacterCard {
    private static final long serialVersionUID = 1L;
    private final InventionIcon inventionIcon;

    /**
     * Creates an Inventor card.
     *
     * @param inventionIcon the invention icon on this card
     */
    public Inventor(String name, Era era, Set<PlayerCount> allowedPlayerCounts, InventionIcon inventionIcon) {
        super(name, era, allowedPlayerCounts);
        this.inventionIcon = inventionIcon;
    }

    @Override
    public void onPurchase(Player player) {
    }

    @Override
    public CharacterType getType() {
        return CharacterType.INVENTOR;
    }

    /** Returns the invention icon on this card. */
    public InventionIcon getInventionIcon() {
        return inventionIcon;
    }
}
