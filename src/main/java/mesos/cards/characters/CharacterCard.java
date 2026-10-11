package mesos.cards.characters;

import java.util.Set;
import mesos.cards.TribeDeckCard;
import mesos.enums.CharacterType;
import mesos.enums.Era;
import mesos.enums.PlayerCount;
import mesos.player.Player;

/** Abstract base class for all character cards that a player can recruit. */
public abstract class CharacterCard extends TribeDeckCard {
    private static final long serialVersionUID = 1L;

    protected CharacterCard(String name, Era era, Set<PlayerCount> allowedPlayerCounts) {
        super(name, era, allowedPlayerCounts);
    }

    /**
     * Called immediately when this card is purchased and added to the player's tribe.
     *
     * @param player the player who purchased this card
     */
    public abstract void onPurchase(Player player);

    /** Returns the character type of this card. */
    public abstract CharacterType getType();

    /**
     * Returns the base sustenance discount this character contributes.
     * Defaults to zero; overridden by {@link Gatherer}.
     */
    public int getBaseSustenanceDiscount() {
        return 0;
    }

    /**
     * Returns the number of shaman icons on this card.
     * Defaults to zero; overridden by {@link Shaman}.
     */
    public int getShamanIcons() {
        return 0;
    }
}
